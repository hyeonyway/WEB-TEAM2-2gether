package com.dbidding.global.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.sse.NotificationSseConnectionManager;
import com.dbidding.sse.metrics.SseMetrics;
import com.dbidding.wallet.sse.WalletSseConnectionManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class MeSseConnectionManagerTest {

    @Test
    void connect는_새_emitter를_발급해_등록하고_총_연결수에_반영한다() {
        MeSseConnectionManager manager = manager();

        SseEmitter emitter = manager.connect(1, "session-a");

        assertThat(emitter).isNotNull();
        assertThat(manager.emittersFor(1)).containsExactly(emitter);
        assertThat(manager.totalConnectionCount()).isEqualTo(1);
    }

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
    void heartbeat은_주입받은_executor로_등록된_모든_emitter에_전송한다() {
        TaskExecutor executor = mock(TaskExecutor.class);
        MeSseConnectionManager manager = manager(executor);
        manager.register(1, mock(SseEmitter.class));
        manager.register(2, mock(SseEmitter.class));

        manager.heartbeat();

        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void heartbeat은_등록된_emitter에_실제로_heartbeat_주석_이벤트를_전송한다() throws Exception {
        // 위 테스트는 mock executor라 dispatch까지만 확인하고 실제 dispatch된 작업(runnable)은
        // 실행되지 않는다 — SyncTaskExecutor로 실제 emitter.send() 호출까지 검증한다.
        MeSseConnectionManager manager = manager();
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);

        manager.heartbeat();

        // register()의 "connected" 이벤트(1회) + heartbeat(1회) = 총 2회.
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeat은_Async로_notificationFanOutTaskExecutor에서_돈다() throws NoSuchMethodException {
        // heartbeat()가 plain @Scheduled면 앱 전체가 공유하는 단일 스레드
        // TaskScheduler(AuctionSchedulingConfig의 @Primary bean, poolSize=1) 위에서 돌게 되고,
        // notificationFanOutTaskExecutor 포화 시 CallerRunsPolicy가 그 스케줄러 스레드를
        // 블로킹시켜 다른 모든 @Scheduled 작업을 함께 멈출 수 있다(#557 리뷰에서 발견). @Async로
        // 트리거 자체를 executor로 먼저 넘겨서 이 위험을 없앤다 — 이 계약이 깨지지 않게 고정한다.
        Method heartbeat = MeSseConnectionManager.class.getMethod("heartbeat");
        Async async = heartbeat.getAnnotation(Async.class);

        assertThat(async).isNotNull();
        assertThat(async.value()).isEqualTo("notificationFanOutTaskExecutor");
    }

    @Test
    void 알림_지갑_매니저를_같이_생성해도_연결수_gauge는_me_하나만_등록된다() {
        // 알림·지갑이 커넥션을 공유하므로(#557) 셀 대상은 물리적으로 하나뿐이다. 예전에
        // 대시보드 호환 목적으로 NotificationSseConnectionManager/WalletSseConnectionManager
        // 생성자에서도 같은 값을 stream=notification/wallet으로 또 등록했었는데, 그러면
        // 커넥션 하나가 me+notification+wallet 세 시리즈에 다 잡혀서 합산 패널에서 실제
        // 연결 수의 3배로 보인다(#560에서 실제로 발견됨) — 이 회귀가 재발하지 않게 고정한다.
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        MeSseConnectionManager connectionManager =
                new MeSseConnectionManager(new SseMetrics(registry, "me"), new SyncTaskExecutor());
        new NotificationSseConnectionManager(connectionManager, new SseMetrics(registry, "notification"), new ObjectMapper());
        new WalletSseConnectionManager(
                connectionManager, new ObjectMapper(), new SyncTaskExecutor(), new SseMetrics(registry, "wallet"));

        List<String> streamsWithConnectionGauge = registry.find("dbidding.sse.connections").gauges().stream()
                .map(gauge -> gauge.getId().getTag("stream"))
                .toList();

        assertThat(streamsWithConnectionGauge).containsExactly("me");
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
