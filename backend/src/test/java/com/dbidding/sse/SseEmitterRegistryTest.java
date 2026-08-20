package com.dbidding.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRegistryTest {

    @Test
    void 하나의_emitter를_여러_키에_등록하면_각_키_전송_대상에_모두_포함된다() {
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter emitter = mock(SseEmitter.class);

        registry.register(Set.of(10, 20), emitter, null);

        assertThat(registry.emittersFor(10)).containsExactly(emitter);
        assertThat(registry.emittersFor(20)).containsExactly(emitter);
        assertThat(registry.totalConnectionCount()).isEqualTo(1);
    }

    @Test
    void 등록되지_않은_키는_빈_전송_대상을_반환한다() {
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));

        assertThat(registry.emittersFor(999)).isEmpty();
        assertThat(registry.connectionCount(999)).isZero();
    }

    @Test
    void 전송_실패하면_모든_키에서_제거된다() throws Exception {
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(Set.of(10, 20), emitter, null);
        doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        boolean result = registry.send(emitter, SseEmitter.event().comment("ping"));

        assertThat(result).isFalse();
        assertThat(registry.emittersFor(10)).isEmpty();
        assertThat(registry.emittersFor(20)).isEmpty();
        assertThat(registry.totalConnectionCount()).isZero();
        verify(emitter).complete();
    }

    @Test
    void 서로_다른_키의_send가_같은_emitter에_동시에_들어와도_직렬화되어_충돌하지_않는다() throws Exception {
        // 선택 구독(#390)으로 emitter 1개가 여러 키를 구독할 수 있게 되면서, 서로 다른 키의
        // broadcast가 같은 emitter에 대해 동시에 send()를 호출할 수 있다. SseEmitter.send()는
        // 동시 호출을 지원하지 않으므로(회귀: 동시 호출 시 실제 연결이 끊김), 이 테스트는
        // registry.send()가 emitter별로 실제 직렬화하는지 직접 검증한다.
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(Set.of(10, 20), emitter, null);

        AtomicBoolean inProgress = new AtomicBoolean(false);
        AtomicBoolean overlapDetected = new AtomicBoolean(false);
        doAnswer(invocation -> {
            if (!inProgress.compareAndSet(false, true)) {
                overlapDetected.set(true);
            }
            Thread.sleep(50);
            inProgress.set(false);
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothStarted = new CountDownLatch(2);
        Runnable sendTask = () -> {
            bothStarted.countDown();
            registry.send(emitter, SseEmitter.event().comment("x"));
        };
        try {
            var first = executor.submit(sendTask);
            var second = executor.submit(sendTask);
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdown();
        }

        assertThat(overlapDetected).isFalse();
        assertThat(registry.emittersFor(10)).containsExactly(emitter);
        assertThat(registry.emittersFor(20)).containsExactly(emitter);
    }

    @Test
    void 이미_전송_중인_emitter에_보내면_블로킹_없이_즉시_버려진다() throws Exception {
        // #615 — 예전엔 emitter별 send를 ReentrantLock.lock()(무제한 대기)으로 직렬화해서,
        // 죽은 클라이언트 하나가 emitter.send() 안에서 영원히(또는 아주 오래) 블로킹되면
        // 그 뒤로 도착하는 모든 이벤트가 새 가상스레드를 만들어 이 락을 무제한 대기하며
        // 동시성 캡의 permit을 반납 없이 계속 붙잡았다 — 죽은 클라이언트 1개가 전역 캡
        // 전체를 잠식할 수 있었다(슬로우 리더 부하테스트에서 실측). tryLock()+discard로
        // 바꿔서, 이미 전송 중인 emitter에 또 보내려 하면 블로킹 없이 즉시 버려지는지
        // (=호출 스레드가 안 막히는지) 검증한다.
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(Set.of(10), emitter, null);

        CountDownLatch sendStarted = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        doAnswer(invocation -> {
            sendStarted.countDown();
            assertThat(releaseSend.await(2, TimeUnit.SECONDS)).isTrue();
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstSend = executor.submit(
                    () -> registry.send(emitter, SseEmitter.event().comment("first")));
            assertThat(sendStarted.await(2, TimeUnit.SECONDS)).isTrue();

            // firstSend가 아직 emitter.send() 안에서 블로킹 중인 상태에서 두 번째 이벤트를
            // 보낸다 — 락을 무제한 대기하지 않고 즉시(=아주 짧은 시간 안에) 리턴해야 한다.
            Future<Boolean> secondSend = executor.submit(
                    () -> registry.send(emitter, SseEmitter.event().comment("second")));
            boolean discarded = secondSend.get(200, TimeUnit.MILLISECONDS);

            assertThat(discarded).isTrue();
            releaseSend.countDown();
            assertThat(firstSend.get(2, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }

        // "second"는 버려졌으므로 emitter.send()는 connected(등록 시 1회) + first(1회) =
        // 2번만 호출된다.
        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void heartbeatAll은_등록된_모든_emitter에_주석_이벤트를_보낸다() throws Exception {
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        registry.register(Set.of(10), first, null);
        registry.register(Set.of(20), second, null);

        registry.heartbeatAll();

        // register()가 "connected" 이벤트를 이미 1번 보냈으므로 heartbeat까지 합쳐 2번.
        verify(first, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        verify(second, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void disconnectAll은_등록된_모든_emitter를_완료하고_제거한다() {
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter first = mock(SseEmitter.class);
        SseEmitter second = mock(SseEmitter.class);
        registry.register(Set.of(10), first, null);
        registry.register(Set.of(20), second, null);

        registry.disconnectAll();

        assertThat(registry.totalConnectionCount()).isZero();
        verify(first).complete();
        verify(second).complete();
    }

    @Test
    void 세션_레지스트리가_없으면_sessionId를_줘도_세션_연동을_하지_않는다() {
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"));
        SseEmitter emitter = mock(SseEmitter.class);

        boolean result = registry.register(Set.of(10), emitter, "session-a");

        assertThat(result).isTrue();
        assertThat(registry.emittersFor(10)).containsExactly(emitter);
    }

    @Test
    void 세션_레지스트리가_있으면_세션에도_등록되고_세션_종료시_함께_완료된다() {
        SessionSseConnectionRegistry sessionRegistry = new SessionSseConnectionRegistry();
        SseEmitterRegistry<Integer> registry =
                new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"), sessionRegistry);
        SseEmitter emitter = mock(SseEmitter.class);

        registry.register(Set.of(10), emitter, "session-a");
        sessionRegistry.disconnect("session-a");

        verify(emitter).complete();
    }

    @Test
    void 이미_종료된_세션이면_등록을_거부하고_false를_반환한다() {
        SessionSseConnectionRegistry sessionRegistry = new SessionSseConnectionRegistry();
        sessionRegistry.disconnect("session-a");
        SseEmitterRegistry<Integer> registry =
                new SseEmitterRegistry<>(new SseMetrics(new SimpleMeterRegistry(), "test"), sessionRegistry);
        SseEmitter emitter = mock(SseEmitter.class);

        boolean result = registry.register(Set.of(10), emitter, "session-a");

        assertThat(result).isFalse();
        assertThat(registry.emittersFor(10)).isEmpty();
    }

    @Test
    void 연결_해제_콜백에서_close_reason별로_한번씩만_기록된다() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(meterRegistry, "test"));
        SseEmitter emitter = mock(SseEmitter.class);
        final Runnable[] onCompletion = new Runnable[1];
        doAnswer(invocation -> {
            onCompletion[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));

        registry.register(Set.of(10), emitter, null);
        onCompletion[0].run();

        assertThat(meterRegistry.get("dbidding.sse.connections.closed")
                .tag("stream", "test").tag("reason", "completion").counter().count()).isEqualTo(1);
        assertThat(registry.totalConnectionCount()).isZero();
    }

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

    @Test
    void 전용_메트릭_전송이_실패해도_연결_종료_사유는_registry_자신의_메트릭으로_기록한다() throws Exception {
        // register()가 연결 시작 시각을 registry 자신의 "test" 메트릭에 기록해두므로(trackConnectionStart),
        // 종료 사유도 같은 "test" 메트릭으로 기록해야 시작-종료가 짝지어진다. callMetrics(caller)로
        // 기록하면 시작 시각을 못 찾아 조용히 유실된다 — #558 리뷰에서 발견된 버그의 회귀 테스트.
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        SseEmitterRegistry<Integer> registry = new SseEmitterRegistry<>(new SseMetrics(meterRegistry, "test"));
        SseMetrics callMetrics = new SseMetrics(meterRegistry, "caller");
        SseEmitter emitter = mock(SseEmitter.class);
        registry.register(Set.of(10), emitter, null);
        doThrow(new IOException("disconnected")).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        registry.send(emitter, SseEmitter.event().comment("ping"), callMetrics);

        assertThat(meterRegistry.get("dbidding.sse.connections.closed")
                .tag("stream", "test").tag("reason", "send_failure").counter().count()).isEqualTo(1);
        assertThat(meterRegistry.get("dbidding.sse.connections.closed")
                .tag("stream", "caller").tag("reason", "send_failure").counter().count()).isZero();
    }
}
