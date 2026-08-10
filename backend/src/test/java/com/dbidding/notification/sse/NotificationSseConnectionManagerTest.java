package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import java.io.IOException;
import java.time.Instant;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.dbidding.global.security.session.SessionSseConnectionRegistry;

class NotificationSseConnectionManagerTest {

    @Test
    void 연결_등록과_해제에_따라_알림_SSE_연결_Gauge가_변한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationSseConnectionManager manager = new NotificationSseConnectionManager(
                new SessionSseConnectionRegistry(), new NotificationSseMetrics(registry));
        SseEmitter emitter = mock(SseEmitter.class);
        final Runnable[] onCompletion = new Runnable[1];
        org.mockito.Mockito.doAnswer(invocation -> {
            onCompletion[0] = invocation.getArgument(0);
            return null;
        }).when(emitter).onCompletion(any(Runnable.class));

        manager.register(1, emitter);

        assertThat(registry.get("dbidding.sse.connections").tag("stream", "notification").gauge().value()).isEqualTo(1);
        onCompletion[0].run();
        assertThat(registry.get("dbidding.sse.connections").tag("stream", "notification").gauge().value()).isZero();
    }

    @Test
    void 연결한_emitter에_알림_생성_이벤트를_전송한다() throws Exception {
        NotificationSseConnectionManager manager = manager(new SessionSseConnectionRegistry());
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);

        manager.push(1, notification());

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(manager.connectionCount(1)).isEqualTo(1);
    }

    @Test
    void 다른_유저의_연결에는_전송하지_않는다() throws Exception {
        NotificationSseConnectionManager manager = manager(new SessionSseConnectionRegistry());
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);

        manager.push(2, notification());

        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    void 전송에_실패한_emitter는_연결_목록에서_제거한다() throws Exception {
        NotificationSseConnectionManager manager = manager(new SessionSseConnectionRegistry());
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, emitter);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        manager.push(1, notification());

        assertThat(manager.connectionCount(1)).isZero();
        verify(emitter).complete();
    }

    @Test
    void 세션_연결을_등록하고_전송_실패_시_세션_레지스트리에서도_해제한다() throws Exception {
        SessionSseConnectionRegistry registry = new SessionSseConnectionRegistry();
        NotificationSseConnectionManager manager = manager(registry);
        SseEmitter emitter = mock(SseEmitter.class);
        manager.register(1, "session-a", emitter);
        doThrow(new IOException("disconnected"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        manager.push(1, notification());
        registry.disconnect("session-a");

        assertThat(manager.connectionCount(1)).isZero();
        verify(emitter, times(1)).complete();
    }

    @Test
    void 세션_연결은_세션_레지스트리에_등록된다() {
        SessionSseConnectionRegistry registry = new SessionSseConnectionRegistry();
        NotificationSseConnectionManager manager = manager(registry);
        SseEmitter emitter = mock(SseEmitter.class);

        manager.register(1, "session-a", emitter);
        registry.disconnect("session-a");

        verify(emitter).complete();
    }

    @Test
    void 접속중인_연결이_없으면_아무일도_하지_않는다() {
        NotificationSseConnectionManager manager = manager(new SessionSseConnectionRegistry());

        manager.push(1, notification());

        assertThat(manager.connectionCount(1)).isZero();
    }

    private NotificationResponse notification() {
        return new NotificationResponse(1L, 100, NotificationType.AUCTION_OPENED, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));
    }

    private NotificationSseConnectionManager manager(SessionSseConnectionRegistry registry) {
        return new NotificationSseConnectionManager(registry, new NotificationSseMetrics(new SimpleMeterRegistry()));
    }
}
