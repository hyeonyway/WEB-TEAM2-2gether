package com.dbidding.notification.sse;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.function.Supplier;
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
    // Micrometer Gauge는 이 Supplier를 약한 참조로만 들고 있어, GC되지 않도록 필드로 붙잡아둔다.
    private final Supplier<Number> connectionCountSupplier;

    public NotificationSseConnectionManager(
            MeSseConnectionManager connectionManager,
            @Qualifier("notificationSseMetrics") SseMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.connectionManager = connectionManager;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        // 알림·지갑이 이제 커넥션을 공유하므로(#557) 이 값은 공유 커넥션 수와 같다 — 기존
        // dbidding.sse.connections{stream=notification} 대시보드/알림이 계속 값을 받게 유지한다.
        this.connectionCountSupplier = connectionManager::totalConnectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
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
