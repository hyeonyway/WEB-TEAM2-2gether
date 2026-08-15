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
}
