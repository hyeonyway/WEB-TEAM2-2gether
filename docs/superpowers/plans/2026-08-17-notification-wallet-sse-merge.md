# Notification/Wallet SSE Stream Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge the notification SSE stream (`GET /api/me/notifications/stream`) and wallet SSE stream (`GET /api/me/wallet/stream`) into a single per-user SSE connection (`GET /api/me/stream`), cutting SSE connections per logged-in user from 3 (auction/notification/wallet) to 2.

**Architecture:** A new shared `MeSseConnectionManager` owns one `SseEmitterRegistry<Integer>` keyed by userId (registration, per-emitter send serialization, session-termination wiring, heartbeat, connection-count metrics). `NotificationSseConnectionManager` and `WalletSseConnectionManager` keep their own push-time logic (payload shape, JSON serialization, per-domain send-duration/failure metrics, domain-specific dispatch executor) but delegate emitter lookup and the actual `send()` call to the shared manager instead of owning a private registry. One merged controller (`SessionMeSseController`) replaces the two old controllers. On the frontend, `useWalletStream` and `useNotificationStream` are replaced by a single `useMeStream` hook that opens one `EventSource` and dispatches by SSE `event:` name.

**Tech Stack:** Spring Boot (Java 21), Spring `SseEmitter`, Micrometer, React + TanStack Query, Vitest.

## Global Constraints

- Every existing `push(Integer userId, PayloadType payload)` call site (`SynchronousNotificationPushDispatcher`, `VirtualThreadNotificationPushDispatcher`, `LocalWalletSsePublisher`, `WalletSseRedisSubscriber`) must compile and behave unchanged — do not touch these files.
- `SseLoadTestStatusController` calls `notificationSse.totalConnectionCount()` — this method must keep working after the refactor (it now reports the shared `/api/me/stream` connection count).
- `SessionSseConnectionRegistry` is unchanged — it is already session-ID-keyed and domain-agnostic (confirmed: `emittersBySessionId` already supports multiple emitters per session).
- Do not change `auction` SSE (`AuctionSseConnectionManager`, `/api/auctions/stream`) — out of scope (see design doc §2).
- Existing Grafana panels reference `dbidding.sse.connections{stream="notification"}` and `{stream="wallet"}` — do not repurpose those tag values for something else. New connection-level metrics use `stream="me"`.
- Existing Grafana panels reference `dbidding.notification.sse.send.duration`/`.failures` and `dbidding.wallet.sse.send.duration`/`.failures` — these must keep being produced with the same names after the merge (per-domain push still uses its own `SseMetrics`).

---

## Backend

### Task 1: `SseEmitterRegistry` — per-call metrics overload for `send()`

**Files:**
- Modify: `backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java:93-109`
- Test: `backend/src/test/java/com/dbidding/sse/SseEmitterRegistryTest.java`

**Interfaces:**
- Produces: `SseEmitterRegistry<K>.send(SseEmitter emitter, SseEmitter.SseEventBuilder event, SseMetrics callMetrics): boolean` — new public overload. Existing `send(SseEmitter, SseEmitter.SseEventBuilder): boolean` keeps its exact current behavior (delegates to the new overload using the registry's own `metrics` field).

- [ ] **Step 1: Write the failing tests**

Add to `backend/src/test/java/com/dbidding/sse/SseEmitterRegistryTest.java` (add these two `@Test` methods inside the existing `SseEmitterRegistryTest` class, after `연결_해제_콜백에서_close_reason별로_한번씩만_기록된다`):

```java
    @Test
    void 전용_메트릭을_전달하면_해당_메트릭으로_전송시간을_기록한다() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(meterRegistry, "test"));
        SseMetrics callMetrics = new SseMetrics(meterRegistry, "caller");
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(Set.of(10), emitter, null);

        registry.send(emitter, SseEmitter.event().comment("ping"), callMetrics);

        assertThat(meterRegistry.get("dbidding.caller.sse.send.duration").timer().count()).isEqualTo(1);
        // register()의 "connected" 이벤트 전송은 registry 자신의 "test" 메트릭을 쓴다 — 그건
        // 그대로 1이고, 방금 보낸 callMetrics(caller) 전송으로는 늘지 않아야 한다.
        assertThat(meterRegistry.get("dbidding.test.sse.send.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void 전용_메트릭_전송이_실패하면_그_메트릭으로만_실패를_기록하고_모든_키에서_제거된다() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(meterRegistry, "test"));
        SseMetrics callMetrics = new SseMetrics(meterRegistry, "caller");
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(Set.of(10, 20), emitter, null);
        doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        boolean result = registry.send(emitter, SseEmitter.event().comment("ping"), callMetrics);

        assertThat(result).isFalse();
        assertThat(meterRegistry.get("dbidding.caller.sse.send.failures").counter().count()).isEqualTo(1);
        // registry 자신의 "test" 메트릭 카운터는 SseMetrics 생성 시 0으로 이미 등록돼 있다 —
        // 이번 실패가 callMetrics(caller)로만 잡히고 "test" 쪽은 여전히 0이어야 한다.
        assertThat(meterRegistry.get("dbidding.test.sse.send.failures").counter().count()).isZero();
        assertThat(registry.emittersFor(10)).isEmpty();
        assertThat(registry.emittersFor(20)).isEmpty();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./gradlew test --tests "com.dbidding.sse.SseEmitterRegistryTest"`
Expected: FAIL — `send(SseEmitter, SseEmitter.SseEventBuilder, SseMetrics)` does not exist (compile error).

- [ ] **Step 3: Implement the overload**

In `backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java`, replace the existing `send` method (currently lines 93-109):

```java
    public boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        return send(emitter, event, metrics);
    }

    /**
     * 여러 도메인이 emitter 등록(연결 관리)은 공유 registry 하나로 통합하면서도, 도메인별
     * 전송시간·실패 카운트({@code dbidding.<stream>.sse.send.*})는 각자의 {@link SseMetrics}로
     * 계속 따로 집계하고 싶을 때 쓰는 오버로드다(#557). emitter별 send 직렬화(락)는 registry가
     * 여전히 하나로 관리하므로, 서로 다른 도메인이 같은 emitter에 동시에 보내도 충돌하지 않는다.
     */
    public boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event, SseMetrics callMetrics) {
        Timer.Sample sample = callMetrics.startSend();
        ReentrantLock sendLock = sendLocksByEmitter.computeIfAbsent(emitter, ignored -> new ReentrantLock());
        sendLock.lock();
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            callMetrics.recordSendFailure();
            callMetrics.recordConnectionClosed(emitter, CloseReason.SEND_FAILURE);
            removeAndComplete(emitter);
            return false;
        } finally {
            sendLock.unlock();
            callMetrics.finishSend(sample);
        }
        return true;
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./gradlew test --tests "com.dbidding.sse.SseEmitterRegistryTest"`
Expected: PASS (all tests in the file, including the two new ones and the pre-existing ones).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/dbidding/sse/SseEmitterRegistry.java backend/src/test/java/com/dbidding/sse/SseEmitterRegistryTest.java
git commit -m "feat: SseEmitterRegistry에 도메인별 메트릭을 받는 send 오버로드 추가"
```

---

### Task 2: `MeSseConnectionManager` — shared per-user connection manager

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/session/MeSseConnectionManager.java`
- Create: `backend/src/main/java/com/dbidding/global/security/session/MeSseConfig.java`
- Test: `backend/src/test/java/com/dbidding/global/security/session/MeSseConnectionManagerTest.java`

**Interfaces:**
- Consumes: `SseEmitterRegistry<K>` (Task 1's new overload), `SessionSseConnectionRegistry` (existing, unchanged), `SseMetrics` (existing), `PerConnectionSseSendDispatcher`/`SseSendDispatcher` (existing), bean `notificationFanOutTaskExecutor` (existing, defined in `NotificationExecutorConfig`, works under both default and `sse-virtual-threads` profiles).
- Produces:
  - `MeSseConnectionManager.connect(Integer userId, String sessionId): SseEmitter`
  - `MeSseConnectionManager.emittersFor(Integer userId): Set<SseEmitter>`
  - `MeSseConnectionManager.send(SseEmitter emitter, SseEmitter.SseEventBuilder event, SseMetrics callMetrics): boolean`
  - `MeSseConnectionManager.connectionCount(Integer userId): int`
  - `MeSseConnectionManager.totalConnectionCount(): int`
  - `MeSseConnectionManager.register(Integer userId, SseEmitter emitter): SseEmitter` (public — test/tooling seam used by Task 4/5's tests, which live in different packages)
  - `MeSseConnectionManager.register(Integer userId, String sessionId, SseEmitter emitter): SseEmitter` (public)
  - Spring bean `meSseMetrics` (name), type `SseMetrics`, tag `stream="me"`.

- [ ] **Step 1: Write the failing tests**

Create `backend/src/test/java/com/dbidding/global/security/session/MeSseConnectionManagerTest.java`:

```java
package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class MeSseConnectionManagerTest {

    @Test
    void 유저ID로_등록한_연결만_조회된다() {
        MeSseConnectionManager manager = manager();
        SseEmitter owner = mock(SseEmitter.class);
        SseEmitter otherUser = mock(SseEmitter.class);

        manager.register(1, owner);
        manager.register(2, otherUser);

        assertThat(manager.emittersFor(1)).containsExactly(owner);
        assertThat(manager.emittersFor(2)).containsExactly(otherUser);
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 연결_등록과_해제에_따라_me_SSE_연결_Gauge가_변한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeSseConnectionManager manager = new MeSseConnectionManager(new SseMetrics(registry, "me"), new SyncTaskExecutor());
        SseEmitter emitter = mock(SseEmitter.class);
        final Runnable[] onCompletion = new Runnable[1];
        doAnswer(invocation -> {
            onCompletion[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));

        manager.register(1, emitter);

        assertThat(registry.get("dbidding.sse.connections").tag("stream", "me").gauge().value()).isEqualTo(1);
        onCompletion[0].run();
        assertThat(registry.get("dbidding.sse.connections").tag("stream", "me").gauge().value()).isZero();
        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "me").tag("reason", "completion").counter().count()).isEqualTo(1);
    }

    @Test
    void 세션_종료_시_해당_세션의_연결도_종료한다() {
        SessionSseConnectionRegistry sessionRegistry = new SessionSseConnectionRegistry();
        MeSseConnectionManager manager = new MeSseConnectionManager(
                sessionRegistry, new SseMetrics(new SimpleMeterRegistry(), "me"), new SyncTaskExecutor());
        SseEmitter emitter = mock(SseEmitter.class);

        manager.register(1, "session-a", emitter);
        sessionRegistry.disconnect("session-a");

        verify(emitter).complete();
    }

    @Test
    void 전송에_실패하면_등록에서_제거된다() throws Exception {
        MeSseConnectionManager manager = manager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);
        doThrow(new IOException("disconnected"))
                .when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        manager.send(emitter, SseEmitter.event().comment("ping"), new SseMetrics(new SimpleMeterRegistry(), "caller"));

        assertThat(manager.connectionCount(1)).isZero();
        verify(emitter).complete();
    }

    @Test
    void heartbeat은_전용_executor로_등록된_모든_emitter에_전송한다() {
        TaskExecutor executor = mock(TaskExecutor.class);
        MeSseConnectionManager manager = manager(executor);
        manager.register(1, mock(SseEmitter.class));
        manager.register(2, mock(SseEmitter.class));

        manager.heartbeat();

        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void 서로_다른_도메인의_전송_메트릭을_각자의_SseMetrics로_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeSseConnectionManager manager = new MeSseConnectionManager(new SseMetrics(registry, "me"), new SyncTaskExecutor());
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);
        SseMetrics notificationMetrics = new SseMetrics(registry, "notification");
        SseMetrics walletMetrics = new SseMetrics(registry, "wallet");

        manager.send(emitter, SseEmitter.event().comment("a"), notificationMetrics);
        manager.send(emitter, SseEmitter.event().comment("b"), walletMetrics);

        assertThat(registry.get("dbidding.notification.sse.send.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.wallet.sse.send.duration").timer().count()).isEqualTo(1);
    }

    private MeSseConnectionManager manager() {
        return manager(new SyncTaskExecutor());
    }

    private MeSseConnectionManager manager(TaskExecutor heartbeatExecutor) {
        return new MeSseConnectionManager(new SseMetrics(new SimpleMeterRegistry(), "me"), heartbeatExecutor);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.dbidding.global.security.session.MeSseConnectionManagerTest"`
Expected: FAIL — `MeSseConnectionManager` class does not exist (compile error).

- [ ] **Step 3: Implement `MeSseConnectionManager` and `MeSseConfig`**

Create `backend/src/main/java/com/dbidding/global/security/session/MeSseConnectionManager.java`:

```java
package com.dbidding.global.security.session;

import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseEmitterRegistry;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 유저 한 명이 여는 개인화 SSE 커넥션(알림/지갑)을 하나로 공유한다(#557). 도메인별
 * push는 여전히 {@code NotificationSseConnectionManager}/{@code WalletSseConnectionManager}가
 * 담당하지만, 실제 emitter 등록·전송 직렬화·세션 연동·heartbeat·연결 수 집계는 이 클래스가
 * 갖고 있는 registry 하나로 합쳐서, 유저당 SSE 커넥션이 3개(auction/notification/wallet)에서
 * 2개로 줄어든다.
 */
@Component
public class MeSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final SseEmitterRegistry<Integer> registry;
    private final SseSendDispatcher heartbeatDispatcher;
    // Micrometer Gauge는 이 Supplier를 약한 참조로만 들고 있어, GC되지 않도록 필드로 붙잡아둔다.
    private final Supplier<Number> connectionCountSupplier;

    @Autowired
    public MeSseConnectionManager(
            SessionSseConnectionRegistry sessionRegistry,
            @Qualifier("meSseMetrics") SseMetrics metrics,
            @Qualifier("notificationFanOutTaskExecutor") TaskExecutor heartbeatExecutor
    ) {
        this.registry = new SseEmitterRegistry<>(metrics, sessionRegistry);
        this.heartbeatDispatcher = new PerConnectionSseSendDispatcher(heartbeatExecutor);
        this.connectionCountSupplier = registry::totalConnectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
    }

    /**
     * 테스트 편의 생성자 — {@code notification.sse}/{@code wallet.sse} 패키지의 도메인
     * 매니저 테스트에서도 공유 연결 관리자를 직접 만들어 써야 해서(#557) public이다.
     */
    public MeSseConnectionManager(SseMetrics metrics, TaskExecutor heartbeatExecutor) {
        this(new SessionSseConnectionRegistry(), metrics, heartbeatExecutor);
    }

    public SseEmitter connect(Integer userId, String sessionId) {
        return register(userId, sessionId, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    public SseEmitter register(Integer userId, SseEmitter emitter) {
        return register(userId, null, emitter);
    }

    public SseEmitter register(Integer userId, String sessionId, SseEmitter emitter) {
        registry.register(Set.of(userId), emitter, sessionId);
        return emitter;
    }

    public Set<SseEmitter> emittersFor(Integer userId) {
        return registry.emittersFor(userId);
    }

    public boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event, SseMetrics callMetrics) {
        return registry.send(emitter, event, callMetrics);
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        registry.allEmitters().forEach(emitter ->
                heartbeatDispatcher.dispatch(() -> registry.send(emitter, SseEmitter.event().comment("heartbeat"))));
    }

    public int connectionCount(Integer userId) {
        return registry.connectionCount(userId);
    }

    public int totalConnectionCount() {
        return registry.totalConnectionCount();
    }
}
```

Create `backend/src/main/java/com/dbidding/global/security/session/MeSseConfig.java`:

```java
package com.dbidding.global.security.session;

import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** #557 — {@code MeSseConnectionManager}(알림·지갑 통합 SSE)의 메트릭 배선. */
@Configuration
public class MeSseConfig {
    @Bean(name = "meSseMetrics")
    public SseMetrics meSseMetrics(MeterRegistry meterRegistry, Clock clock) {
        return new SseMetrics(meterRegistry, "me", clock);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.dbidding.global.security.session.MeSseConnectionManagerTest"`
Expected: PASS (all 6 tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/dbidding/global/security/session/MeSseConnectionManager.java backend/src/main/java/com/dbidding/global/security/session/MeSseConfig.java backend/src/test/java/com/dbidding/global/security/session/MeSseConnectionManagerTest.java
git commit -m "feat: 알림·지갑 SSE가 공유할 MeSseConnectionManager 추가"
```

---

### Task 3: Merge controllers into `SessionMeSseController`

**Files:**
- Create: `backend/src/main/java/com/dbidding/global/security/session/SessionMeSseController.java`
- Test: `backend/src/test/java/com/dbidding/global/security/session/SessionMeSseControllerTest.java`
- Delete: `backend/src/main/java/com/dbidding/global/security/session/SessionNotificationSseController.java`
- Delete: `backend/src/main/java/com/dbidding/global/security/session/SessionWalletSseController.java`

**Interfaces:**
- Consumes: `MeSseConnectionManager.connect(Integer, String)` (Task 2).
- Produces: `GET /api/me/stream` (replaces `GET /api/me/notifications/stream` and `GET /api/me/wallet/stream`).

- [ ] **Step 1: Write the failing test**

Create `backend/src/test/java/com/dbidding/global/security/session/SessionMeSseControllerTest.java`:

```java
package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SessionMeSseControllerTest {

    @Test
    void 세션ID로_스트림을_연결하고_캐시_비활성화_헤더를_설정한다() {
        MeSseConnectionManager connectionManager = mock(MeSseConnectionManager.class);
        SseEmitter emitter = mock(SseEmitter.class);
        when(connectionManager.connect(1, "session-a")).thenReturn(emitter);
        SessionMeSseController controller = new SessionMeSseController(connectionManager);
        HttpSession session = mock(HttpSession.class);
        when(session.getId()).thenReturn("session-a");
        HttpServletResponse response = mock(HttpServletResponse.class);

        SseEmitter result = controller.stream(1, session, response);

        assertThat(result).isSameAs(emitter);
        verify(response).setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        verify(response).setHeader("X-Accel-Buffering", "no");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.dbidding.global.security.session.SessionMeSseControllerTest"`
Expected: FAIL — `SessionMeSseController` class does not exist (compile error).

- [ ] **Step 3: Create the controller, delete the two old ones**

Create `backend/src/main/java/com/dbidding/global/security/session/SessionMeSseController.java`:

```java
package com.dbidding.global.security.session;

import com.dbidding.global.security.CurrentUser;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
public class SessionMeSseController {
    private final MeSseConnectionManager connectionManager;

    @GetMapping(value = "/api/me/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@CurrentUser Integer userId, HttpSession session, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        return connectionManager.connect(userId, session.getId());
    }
}
```

Delete the two old controller files:

```bash
git rm backend/src/main/java/com/dbidding/global/security/session/SessionNotificationSseController.java
git rm backend/src/main/java/com/dbidding/global/security/session/SessionWalletSseController.java
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.dbidding.global.security.session.SessionMeSseControllerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/dbidding/global/security/session/SessionMeSseController.java backend/src/test/java/com/dbidding/global/security/session/SessionMeSseControllerTest.java
git commit -m "feat: 알림·지갑 SSE 컨트롤러를 /api/me/stream 하나로 병합"
```

---

### Task 4: Refactor `NotificationSseConnectionManager` to delegate to `MeSseConnectionManager`

**Files:**
- Modify: `backend/src/main/java/com/dbidding/notification/sse/NotificationSseConnectionManager.java`
- Modify: `backend/src/test/java/com/dbidding/notification/sse/NotificationSseConnectionManagerTest.java`

**Interfaces:**
- Consumes: `MeSseConnectionManager.emittersFor(Integer)`, `.send(SseEmitter, SseEmitter.SseEventBuilder, SseMetrics)`, `.connectionCount(Integer)`, `.totalConnectionCount()` (Task 2).
- Produces: `push(Integer, NotificationResponse)`, `connectionCount(Integer): int`, `totalConnectionCount(): int` — unchanged signatures, so `SynchronousNotificationPushDispatcher`, `VirtualThreadNotificationPushDispatcher`, and `SseLoadTestStatusController` need no changes.

- [ ] **Step 1: Write the failing tests (full replacement)**

Replace the entire contents of `backend/src/test/java/com/dbidding/notification/sse/NotificationSseConnectionManagerTest.java`:

```java
package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class NotificationSseConnectionManagerTest {

    @Test
    void 연결한_emitter에_알림_생성_이벤트를_전송한다() throws Exception {
        MeSseConnectionManager connectionManager = meSseConnectionManager();
        NotificationSseConnectionManager manager = manager(connectionManager);
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(1, emitter);

        manager.push(1, notification());

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 여러_알림_SSE_연결에는_payload를_한번만_직렬화해_전송한다() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any(NotificationResponse.class))).thenReturn("{}");
        MeSseConnectionManager connectionManager = meSseConnectionManager();
        NotificationSseConnectionManager manager = new NotificationSseConnectionManager(
                connectionManager, new SseMetrics(new SimpleMeterRegistry(), "notification"), objectMapper);
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        connectionManager.register(1, first);
        connectionManager.register(1, second);

        NotificationResponse payload = notification();
        SseEmitter.SseEventBuilder event = mock(SseEmitter.SseEventBuilder.class);
        when(event.name(any())).thenReturn(event);
        when(event.data(any(), any(MediaType.class))).thenReturn(event);

        try (org.mockito.MockedStatic<SseEmitter> sseEmitter = org.mockito.Mockito.mockStatic(SseEmitter.class)) {
            sseEmitter.when(SseEmitter::event).thenReturn(event);
            manager.push(1, payload);
        }

        verify(objectMapper).writeValueAsString(payload);
        verify(event, times(2)).data("{}", MediaType.APPLICATION_JSON);
        verify(first, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 다른_유저의_연결에는_전송하지_않는다() throws Exception {
        MeSseConnectionManager connectionManager = meSseConnectionManager();
        NotificationSseConnectionManager manager = manager(connectionManager);
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(1, emitter);

        manager.push(2, notification());

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 전송에_실패한_emitter는_연결_목록에서_제거한다() throws Exception {
        MeSseConnectionManager connectionManager = meSseConnectionManager();
        NotificationSseConnectionManager manager = manager(connectionManager);
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(1, emitter);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        manager.push(1, notification());

        assertThat(manager.connectionCount(1)).isZero();
        verify(emitter).complete();
    }

    @Test
    void 접속중인_연결이_없으면_아무일도_하지_않는다() {
        NotificationSseConnectionManager manager = manager(meSseConnectionManager());

        manager.push(1, notification());

        assertThat(manager.connectionCount(1)).isZero();
    }

    private NotificationResponse notification() {
        return new NotificationResponse(1L, 100, NotificationType.AUCTION_OPENED, 0L, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));
    }

    private NotificationSseConnectionManager manager(MeSseConnectionManager connectionManager) {
        return new NotificationSseConnectionManager(
                connectionManager,
                new SseMetrics(new SimpleMeterRegistry(), "notification"),
                objectMapper());
    }

    private MeSseConnectionManager meSseConnectionManager() {
        return new MeSseConnectionManager(new SseMetrics(new SimpleMeterRegistry(), "me"), new SyncTaskExecutor());
    }

    private ObjectMapper objectMapper() {
        return JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }
}
```

Note: `MeSseConnectionManager(SseMetrics, TaskExecutor)` and `.register(Integer, SseEmitter)` are package-private/public as defined in Task 2 — both are accessible here because they are `public` (Task 2 made `register` public specifically for this cross-package use).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.dbidding.notification.sse.NotificationSseConnectionManagerTest"`
Expected: FAIL — `NotificationSseConnectionManager` constructor doesn't accept `MeSseConnectionManager` yet (compile error).

- [ ] **Step 3: Refactor `NotificationSseConnectionManager`**

Replace the entire contents of `backend/src/main/java/com/dbidding/notification/sse/NotificationSseConnectionManager.java`:

```java
package com.dbidding.notification.sse;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class NotificationSseConnectionManager {
    static final String NOTIFICATION_CREATED_EVENT = "notification-created";

    private final MeSseConnectionManager connectionManager;
    private final SseMetrics metrics;
    private final ObjectMapper objectMapper;

    public NotificationSseConnectionManager(
            MeSseConnectionManager connectionManager,
            @Qualifier("notificationSseMetrics") SseMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.connectionManager = connectionManager;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
    }

    public void push(Integer userId, NotificationResponse payload) {
        Set<SseEmitter> emitters = connectionManager.emittersFor(userId);
        if (emitters.isEmpty()) {
            return; // 접속 중인 탭 없음 — REST 목록 조회로 나중에 확인 가능
        }
        String serializedPayload = writeJson(payload);
        emitters.forEach(emitter -> connectionManager.send(
                emitter,
                SseEmitter.event().name(NOTIFICATION_CREATED_EVENT)
                        .data(serializedPayload, MediaType.APPLICATION_JSON),
                metrics
        ));
    }

    public int connectionCount(Integer userId) {
        return connectionManager.connectionCount(userId);
    }

    public int totalConnectionCount() {
        return connectionManager.totalConnectionCount();
    }

    private String writeJson(NotificationResponse payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("event=notification.sse.payload_serialize_failed notificationId={}", payload.id(), exception);
            throw new IllegalStateException("Notification SSE payload 직렬화 실패", exception);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.dbidding.notification.sse.NotificationSseConnectionManagerTest"`
Expected: PASS.

Also run the two callers' existing tests to confirm no regression (they should be unaffected since `push()`'s signature didn't change):

Run: `cd backend && ./gradlew test --tests "com.dbidding.notification.sse.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/dbidding/notification/sse/NotificationSseConnectionManager.java backend/src/test/java/com/dbidding/notification/sse/NotificationSseConnectionManagerTest.java
git commit -m "refactor: NotificationSseConnectionManager가 공유 MeSseConnectionManager에 위임"
```

---

### Task 5: Refactor `WalletSseConnectionManager` to delegate to `MeSseConnectionManager`

**Files:**
- Modify: `backend/src/main/java/com/dbidding/wallet/sse/WalletSseConnectionManager.java`
- Modify: `backend/src/test/java/com/dbidding/wallet/sse/WalletSseConnectionManagerTest.java`

**Interfaces:**
- Consumes: `MeSseConnectionManager` (Task 2), existing `walletSseTaskExecutor`/`walletSseMetrics` beans (unchanged, defined in `WalletSseExecutorConfig` — do not modify that file).
- Produces: `push(Integer, WalletSsePayload)`, `connectionCount(Integer): int`, `totalConnectionCount(): int` — unchanged signatures, so `LocalWalletSsePublisher` and `WalletSseRedisSubscriber` need no changes.

- [ ] **Step 1: Write the failing tests (full replacement)**

Replace the entire contents of `backend/src/test/java/com/dbidding/wallet/sse/WalletSseConnectionManagerTest.java`:

```java
package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.sse.metrics.SseMetrics;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class WalletSseConnectionManagerTest {

    @Test
    void 지갑_이벤트는_같은_사용자의_연결에만_전송한다() throws Exception {
        MeSseConnectionManager connectionManager = meSseConnectionManager(new SyncTaskExecutor());
        WalletSseConnectionManager manager = manager(connectionManager, new SyncTaskExecutor());
        SseEmitter owner = mock(SseEmitter.class);
        SseEmitter otherUser = mock(SseEmitter.class);
        connectionManager.register(1, owner);
        connectionManager.register(2, otherUser);

        manager.push(1, payload(10));

        verify(owner, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(otherUser, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 지갑_이벤트_전송은_전용_executor에_위임한다() {
        TaskExecutor executor = mock(TaskExecutor.class);
        MeSseConnectionManager connectionManager = meSseConnectionManager(new SyncTaskExecutor());
        WalletSseConnectionManager manager = manager(connectionManager, executor);
        connectionManager.register(1, mock(SseEmitter.class));

        manager.push(1, payload(10));

        verify(executor).execute(any(Runnable.class));
    }

    @Test
    void 전송_실패시_send_failure_원인으로_한번만_기록한다() throws Exception {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MeSseConnectionManager connectionManager = meSseConnectionManager(new SyncTaskExecutor());
        WalletSseConnectionManager manager = new WalletSseConnectionManager(
                connectionManager, objectMapper(), new SyncTaskExecutor(), new SseMetrics(meterRegistry, "wallet"));
        SseEmitter emitter = mock(SseEmitter.class);
        connectionManager.register(1, emitter);
        org.mockito.Mockito.doThrow(new IOException("disconnected"))
                .when(emitter).send(ArgumentMatchers.any(SseEmitter.SseEventBuilder.class));

        manager.push(1, payload(10));

        assertThat(meterRegistry.get("dbidding.wallet.sse.send.failures").counter().count()).isEqualTo(1);
    }

    private WalletSseConnectionManager manager(MeSseConnectionManager connectionManager, TaskExecutor executor) {
        return new WalletSseConnectionManager(connectionManager, objectMapper(), executor, metrics());
    }

    private MeSseConnectionManager meSseConnectionManager(TaskExecutor heartbeatExecutor) {
        return new MeSseConnectionManager(new SseMetrics(new SimpleMeterRegistry(), "me"), heartbeatExecutor);
    }

    private SseMetrics metrics() {
        return new SseMetrics(new SimpleMeterRegistry(), "wallet");
    }

    private WalletSsePayload payload(long version) {
        return WalletSsePayload.from(new WalletBalanceChangedEvent(
                1, new WalletBalanceResponse(10_000L, 1_000L, 9_000L), version, Instant.parse("2026-08-12T00:00:00Z")
        ));
    }

    private ObjectMapper objectMapper() {
        return JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests "com.dbidding.wallet.sse.WalletSseConnectionManagerTest"`
Expected: FAIL — `WalletSseConnectionManager` constructor doesn't accept `MeSseConnectionManager` yet (compile error).

- [ ] **Step 3: Refactor `WalletSseConnectionManager`**

Replace the entire contents of `backend/src/main/java/com/dbidding/wallet/sse/WalletSseConnectionManager.java`:

```java
package com.dbidding.wallet.sse;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WalletSseConnectionManager {
    public static final String WALLET_STATE_CHANGED = "wallet-state-changed";

    private final MeSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final SseMetrics metrics;
    private final SseSendDispatcher sendDispatcher;

    @Autowired
    public WalletSseConnectionManager(
            MeSseConnectionManager connectionManager,
            ObjectMapper objectMapper,
            @Qualifier("walletSseTaskExecutor") TaskExecutor sendExecutor,
            @Qualifier("walletSseMetrics") SseMetrics metrics
    ) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.sendDispatcher = new PerConnectionSseSendDispatcher(sendExecutor);
    }

    public void push(Integer userId, WalletSsePayload payload) {
        Set<SseEmitter> emitters = connectionManager.emittersFor(userId);
        if (emitters.isEmpty()) {
            return;
        }
        String serialized = serialize(payload);
        emitters.forEach(emitter -> sendDispatcher.dispatch(() -> connectionManager.send(
                emitter,
                SseEmitter.event().name(WALLET_STATE_CHANGED).data(serialized, MediaType.APPLICATION_JSON),
                metrics
        )));
    }

    public int connectionCount(Integer userId) {
        return connectionManager.connectionCount(userId);
    }

    public int totalConnectionCount() {
        return connectionManager.totalConnectionCount();
    }

    private String serialize(WalletSsePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Wallet SSE payload 직렬화 실패", exception);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests "com.dbidding.wallet.sse.WalletSseConnectionManagerTest"`
Expected: PASS.

Run the callers' existing tests too:

Run: `cd backend && ./gradlew test --tests "com.dbidding.wallet.sse.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/dbidding/wallet/sse/WalletSseConnectionManager.java backend/src/test/java/com/dbidding/wallet/sse/WalletSseConnectionManagerTest.java
git commit -m "refactor: WalletSseConnectionManager가 공유 MeSseConnectionManager에 위임"
```

---

### Task 6: Full backend build/test sanity pass

**Files:**
- None (verification-only task).

**Interfaces:**
- Consumes: everything from Tasks 1-5.
- Produces: nothing new — this task exists to catch anything the per-task test runs above missed (e.g. `WalletSseExecutorConfigTest`, `NotificationExecutorConfigTest`, or `SseLoadTestStatusController` usages that reference removed methods).

- [ ] **Step 1: Run the full backend test suite**

Run: `cd backend && ./gradlew test`
Expected: PASS. If `WalletSseExecutorConfigTest` or `NotificationExecutorConfigTest` fail because they reference `WalletSseConnectionManager`/`NotificationSseConnectionManager`'s old `register`/`connect`/`heartbeat` methods directly, update those specific assertions to use the new `MeSseConnectionManager`-based construction shown in Tasks 4-5 — do not change `WalletSseExecutorConfig.java`/`NotificationExecutorConfig.java` themselves (their executor/metrics bean definitions are unchanged).

- [ ] **Step 2: Grep for any remaining reference to the deleted endpoints or classes**

Run: `cd backend && grep -rn "notifications/stream\|wallet/stream\|SessionNotificationSseController\|SessionWalletSseController" src/main/java src/test/java`
Expected: no matches outside of comments/docs. If `AuctionSseController`'s javadoc or unrelated code mentions these paths as examples, leave it — only fix actual compiled references.

- [ ] **Step 3: Commit (only if Step 1 required fixes)**

```bash
git add -A
git commit -m "fix: 백엔드 SSE 통합 이후 남은 테스트/참조 정리"
```

Skip this step if Step 1 passed with no changes needed.

---

## Frontend

### Task 7: `useMeStream` hook — merged notification + wallet stream

**Files:**
- Create: `frontend/src/hooks/useMeStream.ts`
- Test: `frontend/src/hooks/useMeStream.test.tsx`

**Interfaces:**
- Consumes: `fetchWalletBalance` (`../api/walletApi`), `walletQueryKeys` (`../queries/walletQueryKeys`), `applyNotificationCreated`/`notificationQueryKeys` (`../queries/notificationStreamCache`, `../queries/notificationQueries`), `notificationDedupKey` (`../utils/notificationKey`), `getSessionUserId` (`../auth/session/sessionAuthStore`), `revalidateSession` (`../auth/session/sessionRevalidation`), `isMockApiEnabled` (`../api/mockApiConfig`), `nextSseReconnectDelayMs`/`shouldRevalidateSession` (`./sseReconnectPolicy`) — all pre-existing, unchanged.
- Produces: `useMeStream(options?: {enabled?: boolean; onNotificationCreated?: (notification: NotificationDto) => void}): void`. Opens exactly one `EventSource` at `${VITE_API_BASE_URL}/api/me/stream`, listening for `wallet-state-changed` and `notification-created` events.

- [ ] **Step 1: Write the failing tests**

Create `frontend/src/hooks/useMeStream.test.tsx`:

```tsx
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {act,renderHook} from '@testing-library/react';
import type {ReactNode} from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {fetchWalletBalance} from '../api/walletApi';
import {getSessionUserId,setSession} from '../auth/session/sessionAuthStore';
import {notificationQueryKeys} from '../queries/notificationQueries';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import {useMeStream} from './useMeStream';

vi.mock('../api/walletApi',()=>({fetchWalletBalance:vi.fn()}));

class EventSourceMock extends EventTarget{
  static instances:EventSourceMock[]=[];
  close=vi.fn();
  onopen:((event:Event)=>void)|null=null;
  onerror:((event:Event)=>void)|null=null;
  constructor(public url:string|URL){super();EventSourceMock.instances.push(this);}
}

const fetchWalletBalanceMock=vi.mocked(fetchWalletBalance);

function walletPayload(version:number,totalBalance:number){return JSON.stringify({
  wallet_version:version,total_balance:totalBalance,frozen_balance:1_000,
  available_balance:totalBalance-1_000,updated_at:'2026-08-12T00:00:00Z',
});}

function notificationPayload(id:number,isRead=false){return JSON.stringify({
  id,auctionId:100,bidId:0,message:'메시지',isRead,createdAt:'2026-07-30T12:00:00Z',
});}

function wrapper(queryClient:QueryClient){
  return ({children}:{children:ReactNode})=><QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
}

describe('useMeStream',()=>{
  beforeEach(()=>{
    EventSourceMock.instances=[];
    vi.stubGlobal('EventSource',EventSourceMock);
    vi.useFakeTimers();
    setSession(1);
  });
  afterEach(()=>{vi.useRealTimers();vi.unstubAllGlobals();vi.clearAllMocks();setSession(null);});

  it('세션이 있으면 /api/me/stream 하나에 연결한다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    expect(EventSourceMock.instances).toHaveLength(1);
    expect(String(EventSourceMock.instances[0]?.url)).toContain('/api/me/stream');
  });

  it('지갑 이벤트를 받으면 지갑 캐시를 갱신한다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const source=EventSourceMock.instances[0];

    act(()=>source?.dispatchEvent(new MessageEvent('wallet-state-changed',{data:walletPayload(5,10_000)})));

    expect(queryClient.getQueryData(walletQueryKeys.balance())).toEqual({
      totalBalance:10_000,frozenBalance:1_000,availableBalance:9_000,walletVersion:5,
    });
  });

  it('알림 이벤트를 받으면 안읽음 카운트를 올리고 콜백을 호출한다',async()=>{
    const onNotificationCreated=vi.fn();
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    queryClient.setQueryData(notificationQueryKeys.unreadCount,0);
    renderHook(()=>useMeStream({onNotificationCreated}),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const source=EventSourceMock.instances[0];

    act(()=>source?.dispatchEvent(new MessageEvent('notification-created',{data:notificationPayload(42)})));

    expect(queryClient.getQueryData(notificationQueryKeys.unreadCount)).toBe(1);
    expect(onNotificationCreated).toHaveBeenCalledOnce();
  });

  it('재연결되면 지갑 재조회와 알림 캐시 무효화를 모두 수행한다',async()=>{
    fetchWalletBalanceMock.mockResolvedValueOnce({
      totalBalance:5_000,frozenBalance:0,availableBalance:5_000,walletVersion:1,
    });
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const invalidateSpy=vi.spyOn(queryClient,'invalidateQueries');
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const first=EventSourceMock.instances[0];
    act(()=>first?.onopen?.(new Event('open')));
    act(()=>first?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    const second=EventSourceMock.instances[1];

    await act(async()=>{second?.onopen?.(new Event('open'));await Promise.resolve();});

    expect(fetchWalletBalanceMock).toHaveBeenCalledOnce();
    expect(invalidateSpy).toHaveBeenCalledWith({queryKey:notificationQueryKeys.all});
  });

  it('연속 실패할수록 재연결 지연이 지수적으로 늘어난다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    act(()=>EventSourceMock.instances[0]?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    expect(EventSourceMock.instances).toHaveLength(2);

    act(()=>EventSourceMock.instances[1]?.onerror?.(new Event('error')));
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    expect(EventSourceMock.instances).toHaveLength(2);
    await act(async()=>{await vi.advanceTimersByTimeAsync(2_000);});
    expect(EventSourceMock.instances).toHaveLength(3);
  });

  it('연속 5회 실패하면 세션을 재검증하고, 만료된 상태면 로그인 상태를 비운다',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(
      new Response(JSON.stringify({code:'SESSION_EXPIRED'}),{status:401,headers:{'Content-Type':'application/json'}}),
    );
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    let delay=2_000;
    for(let i=0;i<5;i++){
      const last=EventSourceMock.instances[EventSourceMock.instances.length-1];
      act(()=>last?.onerror?.(new Event('error')));
      await act(async()=>{await vi.advanceTimersByTimeAsync(delay);});
      delay=Math.min(delay*2,30_000);
    }

    await vi.waitFor(()=>expect(fetchMock).toHaveBeenCalled());
    expect(fetchMock.mock.calls.some(([path])=>path==='/api/auth/me')).toBe(true);
    expect(getSessionUserId()).toBeNull();
  });

  it('enabled가 false면 연결하지 않는다',()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream({enabled:false}),{wrapper:wrapper(queryClient)});

    expect(EventSourceMock.instances).toHaveLength(0);
  });

  it('로그인 상태가 아니면 스트림에 연결하지 않는다',async()=>{
    setSession(null);
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});

    expect(EventSourceMock.instances).toHaveLength(0);
  });

  it('언마운트 시 연결을 정리한다',async()=>{
    const queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    const {unmount}=renderHook(()=>useMeStream(),{wrapper:wrapper(queryClient)});
    await act(async()=>{await Promise.resolve();});
    const source=EventSourceMock.instances[0];

    unmount();

    expect(source?.close).toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd frontend && npx vitest run src/hooks/useMeStream.test.tsx`
Expected: FAIL — `./useMeStream` module does not exist.

- [ ] **Step 3: Implement `useMeStream`**

Create `frontend/src/hooks/useMeStream.ts`:

```typescript
import {useEffect,useRef} from 'react';
import type {InfiniteData} from '@tanstack/react-query';
import {useQueryClient} from '@tanstack/react-query';
import {isMockApiEnabled} from '../api/mockApiConfig';
import {fetchWalletBalance} from '../api/walletApi';
import {getSessionUserId} from '../auth/session/sessionAuthStore';
import {revalidateSession} from '../auth/session/sessionRevalidation';
import type {NotificationDto,NotificationPageDto} from '../dto/notificationDto';
import type {WalletBalanceDto} from '../dto/walletDto';
import {applyNotificationCreated} from '../queries/notificationStreamCache';
import {notificationQueryKeys} from '../queries/notificationQueries';
import {walletQueryKeys} from '../queries/walletQueryKeys';
import {notificationDedupKey} from '../utils/notificationKey';
import {nextSseReconnectDelayMs,shouldRevalidateSession} from './sseReconnectPolicy';

const NOTIFICATION_CREATED_EVENT='notification-created';
const WALLET_STATE_CHANGED_EVENT='wallet-state-changed';

type WalletSsePayload={
  wallet_version:number;
  total_balance:number;
  frozen_balance:number;
  available_balance:number;
  updated_at:string;
};

function streamUrl():string{
  const apiBaseUrl=(import.meta.env.VITE_API_BASE_URL??'').replace(/\/+$/,'');
  return `${apiBaseUrl}/api/me/stream`;
}

function parseWalletPayload(data:string):WalletSsePayload|null{
  try{
    const payload=JSON.parse(data) as Partial<WalletSsePayload>;
    if(!Number.isSafeInteger(payload.wallet_version)||!Number.isSafeInteger(payload.total_balance)
      ||!Number.isSafeInteger(payload.frozen_balance)||!Number.isSafeInteger(payload.available_balance)
      ||typeof payload.updated_at!=='string')return null;
    return payload as WalletSsePayload;
  }catch{return null;}
}

function parseNotificationPayload(data:string):NotificationDto|null{
  try{
    const raw=JSON.parse(data) as Partial<NotificationDto>;
    if(
      typeof raw.id!=='number'
      ||typeof raw.auctionId!=='number'
      ||typeof raw.bidId!=='number'
      ||typeof raw.message!=='string'
      ||typeof raw.isRead!=='boolean'
      ||typeof raw.createdAt!=='string'
    )return null;
    return raw as NotificationDto;
  }catch{
    return null;
  }
}

type UseMeStreamOptions={
  enabled?:boolean;
  onNotificationCreated?:(notification:NotificationDto)=>void;
};

export function useMeStream({
  enabled=true,
  onNotificationCreated,
}:UseMeStreamOptions={}):void{
  const queryClient=useQueryClient();
  const onNotificationCreatedRef=useRef(onNotificationCreated);
  onNotificationCreatedRef.current=onNotificationCreated;
  const seenNotificationKeysRef=useRef(new Set<string>());
  const highestWalletVersion=useRef(-1);

  useEffect(()=>{
    if(!enabled||isMockApiEnabled()){
      highestWalletVersion.current=-1;
      return;
    }

    let eventSource:EventSource|null=null;
    let reconnectTimer:ReturnType<typeof setTimeout>|null=null;
    let stopped=false;
    let opened=false;
    let consecutiveFailures=0;

    const handleWalletStateChanged=(event:Event)=>{
      const payload=parseWalletPayload((event as MessageEvent<string>).data);
      if(!payload||payload.wallet_version<=highestWalletVersion.current)return;
      highestWalletVersion.current=payload.wallet_version;
      queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),{
        totalBalance:payload.total_balance,
        frozenBalance:payload.frozen_balance,
        availableBalance:payload.available_balance,
        walletVersion:payload.wallet_version,
      });
    };

    const handleNotificationCreated=(event:Event)=>{
      const notification=parseNotificationPayload((event as MessageEvent<string>).data);
      if(!notification)return;
      const dedupKey=notificationDedupKey(notification);
      if(seenNotificationKeysRef.current.has(dedupKey))return;
      seenNotificationKeysRef.current.add(dedupKey);

      queryClient.setQueryData<InfiniteData<NotificationPageDto>>(
        notificationQueryKeys.list(false),
        current=>applyNotificationCreated(current,notification,false),
      );
      queryClient.setQueryData<InfiniteData<NotificationPageDto>>(
        notificationQueryKeys.list(true),
        current=>applyNotificationCreated(current,notification,true),
      );
      if(!notification.isRead){
        queryClient.setQueryData<number>(
          notificationQueryKeys.unreadCount,
          current=>(current??0)+1,
        );
      }
      onNotificationCreatedRef.current?.(notification);
    };

    const recoverWalletBalance=async()=>{
      const versionBeforeRecovery=highestWalletVersion.current;
      try{
        const balance=await fetchWalletBalance();
        if(!stopped&&highestWalletVersion.current===versionBeforeRecovery){
          queryClient.setQueryData<WalletBalanceDto>(walletQueryKeys.balance(),balance);
          if(balance.walletVersion!==undefined){
            highestWalletVersion.current=Math.max(highestWalletVersion.current,balance.walletVersion);
          }
        }
      }catch{
        // 다음 SSE snapshot 또는 기존 화면 조회가 최신 상태를 복구한다.
      }
    };

    const reconnect=()=>{
      if(stopped||reconnectTimer)return;
      const delay=nextSseReconnectDelayMs(consecutiveFailures);
      reconnectTimer=setTimeout(()=>{reconnectTimer=null;void connect();},delay);
    };

    const handleFailure=()=>{
      consecutiveFailures+=1;
      if(shouldRevalidateSession(consecutiveFailures))void revalidateSession();
      reconnect();
    };

    const detach=()=>{
      eventSource?.removeEventListener(WALLET_STATE_CHANGED_EVENT,handleWalletStateChanged);
      eventSource?.removeEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
    };

    const connect=async()=>{
      if(stopped)return;
      if(!getSessionUserId()){
        reconnect();
        return;
      }
      if(stopped)return;
      eventSource=new EventSource(streamUrl(),{withCredentials:true});
      eventSource.addEventListener(WALLET_STATE_CHANGED_EVENT,handleWalletStateChanged);
      eventSource.addEventListener(NOTIFICATION_CREATED_EVENT,handleNotificationCreated);
      eventSource.onopen=()=>{
        consecutiveFailures=0;
        if(opened){
          void recoverWalletBalance();
          void queryClient.invalidateQueries({queryKey:notificationQueryKeys.all});
        }
        opened=true;
      };
      eventSource.onerror=()=>{
        detach();
        eventSource?.close();
        eventSource=null;
        handleFailure();
      };
    };

    void connect();

    return()=>{
      stopped=true;
      if(reconnectTimer)clearTimeout(reconnectTimer);
      detach();
      eventSource?.close();
    };
  },[enabled,queryClient]);
}
```

Note: unlike the old `useWalletStream`, `connect()` now waits for `getSessionUserId()` before opening the connection — this matches `useNotificationStream`'s existing (and more correct) behavior, and is safe because `AuthProvider` only flips to `authenticated` after `setSession(...)` has already run.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd frontend && npx vitest run src/hooks/useMeStream.test.tsx`
Expected: PASS (all 9 tests).

- [ ] **Step 5: Commit**

```bash
git add frontend/src/hooks/useMeStream.ts frontend/src/hooks/useMeStream.test.tsx
git commit -m "feat: 알림·지갑 SSE를 하나의 커넥션으로 여는 useMeStream 훅 추가"
```

---

### Task 8: Wire `useMeStream` into `App.tsx`, remove old hooks

**Files:**
- Modify: `frontend/src/app/App.tsx`
- Modify: `frontend/src/auth/AuthProvider.tsx`
- Modify: `frontend/src/auth/AuthProvider.test.tsx`
- Delete: `frontend/src/hooks/useWalletStream.ts`
- Delete: `frontend/src/hooks/useWalletStream.test.tsx`
- Delete: `frontend/src/hooks/useNotificationStream.ts`
- Delete: `frontend/src/hooks/useNotificationStream.test.tsx`

**Interfaces:**
- Consumes: `useMeStream` (Task 7).
- Produces: nothing new — this task only rewires call sites.

- [ ] **Step 1: Update `AuthProvider.tsx` — remove `useWalletStream`**

In `frontend/src/auth/AuthProvider.tsx`, remove the import on line 17:

```typescript
import {useWalletStream} from '../hooks/useWalletStream';
```

And remove the call on line 67:

```typescript
useWalletStream(status === 'authenticated');
```

The surrounding lines (`useWalletCrossTabSync`, the `useEffect` that clears queries on `anonymous`) are unchanged.

- [ ] **Step 2: Update `AuthProvider.test.tsx` — remove the now-unused mock**

In `frontend/src/auth/AuthProvider.test.tsx`, remove line 11:

```typescript
vi.mock('../hooks/useWalletStream', () => ({useWalletStream: vi.fn()}));
```

- [ ] **Step 3: Update `App.tsx` — replace `useNotificationStream` with `useMeStream`**

Replace the contents of `frontend/src/app/App.tsx`:

```tsx
import {useEffect} from 'react';
import {ToastContainer} from '../components';
import NotificationToastStack from '../components/NotificationToastStack';
import {useAuth} from '../auth/useAuth';
import {useCurrentUserId} from '../auth/useCurrentUserId';
import {useMeStream} from '../hooks/useMeStream';
import {useNotificationToasts} from '../hooks/useNotificationToasts';
import {AppRoutes} from './router';

export default function App() {
  const {status} = useAuth();
  const userId = useCurrentUserId();
  const {toasts, push, dismiss, clear} = useNotificationToasts();
  useMeStream({enabled: status === 'authenticated', onNotificationCreated: push});

  useEffect(() => {
    clear();
  }, [userId, clear]);

  return (
    <>
      <AppRoutes/>
      <ToastContainer/>
      <NotificationToastStack toasts={toasts} onDismiss={dismiss}/>
    </>
  );
}
```

- [ ] **Step 4: Delete the old hooks and their tests**

```bash
git rm frontend/src/hooks/useWalletStream.ts frontend/src/hooks/useWalletStream.test.tsx
git rm frontend/src/hooks/useNotificationStream.ts frontend/src/hooks/useNotificationStream.test.tsx
```

- [ ] **Step 5: Run the full frontend test suite and typecheck**

Run: `cd frontend && npx vitest run`
Expected: PASS — no test file should still import `useWalletStream` or `useNotificationStream`. If any other test file does (search first), update its import to `useMeStream` following the same pattern as Task 8 Step 3.

Run: `cd frontend && npx tsc --noEmit`
Expected: no errors. This catches any remaining import of the deleted hook files.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/app/App.tsx frontend/src/auth/AuthProvider.tsx frontend/src/auth/AuthProvider.test.tsx
git commit -m "refactor: App/AuthProvider가 useMeStream 하나로 알림·지갑 SSE를 구독하도록 정리"
```

---

## Final Verification

- [ ] **Backend:** `cd backend && ./gradlew test` — all green.
- [ ] **Frontend:** `cd frontend && npx vitest run && npx tsc --noEmit` — all green.
- [ ] **Manual smoke check (local, `local-sse` profile or dev):** log in, open the wallet page and the notification drawer in the same tab, confirm the browser DevTools Network panel shows exactly one `GET /api/me/stream` EventSource connection (not two), and that both a wallet balance change and a new notification arrive over it.
- [ ] **Load test re-run:** re-run `hot-auction-pattern.js`/`pure-throughput.js` (see [`docs/hyeonmoon/realtime/5-notification-wallet-sse-stream-consolidation-plan.md`](../../hyeonmoon/realtime/5-notification-wallet-sse-stream-consolidation-plan.md) §4) and confirm `sse_connections_max` per user drops from 3 to 2, and total connections at the same `SSE_VUS` drop by ~33%.
