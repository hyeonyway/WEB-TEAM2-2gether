package com.dbidding.notification.sse;

import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.sse.SseEmitterRegistry;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class NotificationSseConnectionManager {
    static final String NOTIFICATION_CREATED_EVENT = "notification-created";
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final SseEmitterRegistry<Integer> registry;
    private final ObjectMapper objectMapper;

    public NotificationSseConnectionManager(
            SessionSseConnectionRegistry sessionRegistry,
            @Qualifier("notificationSseMetrics") SseMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.registry = new SseEmitterRegistry<>(metrics, sessionRegistry);
        this.objectMapper = objectMapper;
        metrics.registerConnectionGauge(registry::totalConnectionCount);
    }

    public SseEmitter connect(Integer userId) {
        return connect(userId, null);
    }

    public SseEmitter connect(Integer userId, String sessionId) {
        return register(userId, sessionId, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    SseEmitter register(Integer userId, SseEmitter emitter) {
        return register(userId, null, emitter);
    }

    SseEmitter register(Integer userId, String sessionId, SseEmitter emitter) {
        registry.register(Set.of(userId), emitter, sessionId);
        return emitter;
    }

    public void push(Integer userId, NotificationResponse payload) {
        Set<SseEmitter> emitters = registry.emittersFor(userId);
        if (emitters.isEmpty()) {
            return; // 접속 중인 탭 없음 — REST 목록 조회로 나중에 확인 가능
        }
        String serializedPayload = writeJson(payload);
        emitters.forEach(emitter -> registry.send(
                emitter,
                SseEmitter.event().name(NOTIFICATION_CREATED_EVENT)
                        .data(serializedPayload, MediaType.APPLICATION_JSON)
        ));
    }

    @Async("notificationFanOutTaskExecutor")
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        registry.heartbeatAll();
    }

    int connectionCount(Integer userId) {
        return registry.connectionCount(userId);
    }

    public int totalConnectionCount() {
        return registry.totalConnectionCount();
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
