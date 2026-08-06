package com.dbidding.notification;

import com.dbidding.notification.dto.NotificationResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class NotificationSseConnectionManager {
    static final String NOTIFICATION_CREATED_EVENT = "notification-created";
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final ConcurrentMap<Integer, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    public SseEmitter connect(Integer userId) {
        return register(userId, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    SseEmitter register(Integer userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArraySet<>());
        emitters.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> removeAndComplete(userId, emitter));
        emitter.onError(error -> removeAndComplete(userId, emitter));

        send(userId, emitter, SseEmitter.event()
                .name("connected")
                .reconnectTime(RECONNECT_TIME_MILLIS)
                .data("connected"));
        return emitter;
    }

    public void push(Integer userId, NotificationResponse payload) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return; // 접속 중인 탭 없음 — REST 목록 조회로 나중에 확인 가능
        }
        emitters.forEach(emitter -> send(
                userId,
                emitter,
                SseEmitter.event().name(NOTIFICATION_CREATED_EVENT).data(payload)
        ));
    }

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

    private void send(Integer userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            removeAndComplete(userId, emitter);
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
        emittersByUserId.computeIfPresent(userId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
