package com.dbidding.wallet.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;
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
    private final ObjectMapper objectMapper;
    private final TaskExecutor sendExecutor;

    public WalletSseConnectionManager(
            ObjectMapper objectMapper,
            @Qualifier("walletSseTaskExecutor") TaskExecutor sendExecutor
    ) {
        this.objectMapper = objectMapper;
        this.sendExecutor = sendExecutor;
    }

    public SseEmitter connect(Integer userId) {
        return register(userId, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    SseEmitter register(Integer userId, SseEmitter emitter) {
        emittersByUserId.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> removeAndComplete(userId, emitter));
        emitter.onError(error -> removeAndComplete(userId, emitter));
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
        emittersByUserId.computeIfPresent(userId, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
    }
}
