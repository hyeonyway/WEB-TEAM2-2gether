package com.dbidding.wallet.sse;

import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class WalletSseConnectionManager {
    public static final String WALLET_STATE_CHANGED = "wallet-state-changed";
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private final ConcurrentMap<Integer, Set<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();
    private final ConcurrentMap<SseEmitter, String> sessionIdByEmitter = new ConcurrentHashMap<>();
    private final SessionSseConnectionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final TaskExecutor sendExecutor;

    @Autowired
    public WalletSseConnectionManager(
            SessionSseConnectionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Qualifier("walletSseTaskExecutor") TaskExecutor sendExecutor
    ) {
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.sendExecutor = sendExecutor;
    }

    /** 기존 단위 테스트의 생성자 계약을 유지한다. */
    WalletSseConnectionManager(ObjectMapper objectMapper, TaskExecutor sendExecutor) {
        this(new SessionSseConnectionRegistry(), objectMapper, sendExecutor);
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
        emittersByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        if (sessionId != null) sessionIdByEmitter.put(emitter, sessionId);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> removeAndComplete(userId, emitter));
        emitter.onError(error -> removeAndComplete(userId, emitter));
        if (sessionId != null && !sessionRegistry.register(sessionId, emitter)) {
            remove(userId, emitter);
            return emitter;
        }
        send(userId, emitter, SseEmitter.event().name("connected").reconnectTime(3_000L).data("connected"));
        return emitter;
    }

    public void push(Integer userId, WalletSsePayload payload) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        String serialized = serialize(payload);
        emitters.forEach(emitter -> sendExecutor.execute(() -> send(userId, emitter,
                SseEmitter.event().name(WALLET_STATE_CHANGED).data(serialized, MediaType.APPLICATION_JSON))));
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emittersByUserId.forEach((userId, emitters) -> emitters.forEach(emitter ->
                sendExecutor.execute(() -> send(userId, emitter, SseEmitter.event().comment("heartbeat")))));
    }

    int connectionCount(Integer userId) {
        Set<SseEmitter> emitters = emittersByUserId.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    private String serialize(WalletSsePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Wallet SSE payload 직렬화 실패", exception);
        }
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
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }

    private void remove(Integer userId, SseEmitter emitter) {
        String sessionId = sessionIdByEmitter.remove(emitter);
        if (sessionId != null) sessionRegistry.unregister(sessionId, emitter);
        emittersByUserId.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
