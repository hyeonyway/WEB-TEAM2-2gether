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
        // 커넥션 수 gauge는 여기서 등록하지 않는다(#560) — 알림·지갑이 커넥션을 공유하므로
        // (#557) 실제로 셀 대상은 하나뿐이고, 그 값은 MeSseConnectionManager가 이미
        // dbidding.sse.connections{stream=me} 하나로 등록한다. 여기서도 같은 값을
        // {stream=notification}으로 또 등록하면(과거엔 대시보드 호환 목적으로 그렇게
        // 했었다) 실제 연결 수가 3배로 잡혀 보이는 문제가 생긴다(#560에서 발견).
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
