package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.notification.domain.NotificationType;
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
