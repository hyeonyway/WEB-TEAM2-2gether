package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;
import io.micrometer.core.instrument.Timer;
import com.dbidding.sse.metrics.SseConnectionCloseMetrics.CloseReason;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.dbidding.global.security.session.SessionSseConnectionRegistry;

@Component
@Slf4j
public class NotificationSseConnectionManager {
    static final String NOTIFICATION_CREATED_EVENT = "notification-created";
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final ConcurrentMap<Integer, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();
    private final ConcurrentMap<SseEmitter, String> sessionIdByEmitter = new ConcurrentHashMap<>();
    private final SessionSseConnectionRegistry sessionRegistry;
    private final NotificationSseMetrics metrics;
    private final ObjectMapper objectMapper;
    private final Supplier<Number> connectionCountSupplier;

    public NotificationSseConnectionManager(
            SessionSseConnectionRegistry sessionRegistry,
            NotificationSseMetrics metrics,
            ObjectMapper objectMapper
    ) {
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.connectionCountSupplier = this::totalConnectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
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
        Timer.Sample connectSample = metrics.startConnect();
        metrics.trackConnectionStart(emitter);
        Set<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);
        if (sessionId != null) {
            sessionIdByEmitter.put(emitter, sessionId);
        }
        emitter.onCompletion(() -> {
            metrics.recordConnectionClosed(emitter, CloseReason.COMPLETION);
            remove(userId, emitter);
        });
        emitter.onTimeout(() -> {
            metrics.recordConnectionClosed(emitter, CloseReason.TIMEOUT);
            removeAndComplete(userId, emitter);
        });
        emitter.onError(error -> {
            metrics.recordConnectionClosed(emitter, CloseReason.ERROR);
            removeAndComplete(userId, emitter);
        });
		if (sessionId != null && !sessionRegistry.register(sessionId, emitter)) {
			remove(userId, emitter);
			return emitter;
		}

        if (send(userId, emitter, SseEmitter.event()
                .name("connected")
                .reconnectTime(RECONNECT_TIME_MILLIS)
                .data("connected"))) {
            metrics.finishConnect(connectSample);
        }
        return emitter;
    }

    public void push(Integer userId, NotificationResponse payload) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return; // 접속 중인 탭 없음 — REST 목록 조회로 나중에 확인 가능
        }
        String serializedPayload = writeJson(payload);
        emitters.forEach(emitter -> send(
                userId,
                emitter,
                SseEmitter.event().name(NOTIFICATION_CREATED_EVENT)
                        .data(serializedPayload, MediaType.APPLICATION_JSON)
        ));
    }

    @Async("notificationFanOutTaskExecutor")
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emittersByUserId.forEach((userId, emitters) -> emitters.forEach(emitter -> send(
                userId, emitter, SseEmitter.event().comment("heartbeat")
        )));
    }

    int connectionCount(Integer userId) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    public int totalConnectionCount() {
        return emittersByUserId.values().stream().mapToInt(Set::size).sum();
    }

    private boolean send(Integer userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            metrics.recordSendFailure();
            metrics.recordConnectionClosed(emitter, CloseReason.SEND_FAILURE);
            removeAndComplete(userId, emitter);
            return false;
        }
        return true;
    }

    private String writeJson(NotificationResponse payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("event=notification.sse.payload_serialize_failed notificationId={}", payload.id(), exception);
            throw new IllegalStateException("Notification SSE payload 직렬화 실패", exception);
        }
    }

    private void removeAndComplete(Integer userId, SseEmitter emitter) {
        remove(userId, emitter);
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 이미 완료된 연결은 제거만 보장한다.
        }
    }

    private void remove(Integer userId, SseEmitter emitter) {
		String sessionId = sessionIdByEmitter.remove(emitter);
		if (sessionId != null) sessionRegistry.unregister(sessionId, emitter);
        emittersByUserId.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
