package com.dbidding.wallet.sse;

import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseEmitterRegistry;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class WalletSseConnectionManager {
    public static final String WALLET_STATE_CHANGED = "wallet-state-changed";
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final SseEmitterRegistry<Integer> registry;
    private final ObjectMapper objectMapper;
    private final SseSendDispatcher sendDispatcher;

    @Autowired
    public WalletSseConnectionManager(
            SessionSseConnectionRegistry sessionRegistry,
            ObjectMapper objectMapper,
            @Qualifier("walletSseTaskExecutor") TaskExecutor sendExecutor,
            @Qualifier("walletSseMetrics") SseMetrics metrics
    ) {
        this.registry = new SseEmitterRegistry<>(metrics, sessionRegistry);
        this.objectMapper = objectMapper;
        this.sendDispatcher = new PerConnectionSseSendDispatcher(sendExecutor);
        metrics.registerConnectionGauge(registry::totalConnectionCount);
    }

    /** 기존 단위 테스트의 생성자 계약을 유지한다. */
    WalletSseConnectionManager(ObjectMapper objectMapper, TaskExecutor sendExecutor, SseMetrics metrics) {
        this(new SessionSseConnectionRegistry(), objectMapper, sendExecutor, metrics);
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

    public int totalConnectionCount() {
        return registry.totalConnectionCount();
    }

    public void push(Integer userId, WalletSsePayload payload) {
        Set<SseEmitter> emitters = registry.emittersFor(userId);
        if (emitters.isEmpty()) {
            return;
        }
        String serialized = serialize(payload);
        emitters.forEach(emitter -> sendDispatcher.dispatch(() -> registry.send(emitter,
                SseEmitter.event().name(WALLET_STATE_CHANGED).data(serialized, MediaType.APPLICATION_JSON))));
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        registry.allEmitters().forEach(emitter ->
                sendDispatcher.dispatch(() -> registry.send(emitter, SseEmitter.event().comment("heartbeat"))));
    }

    int connectionCount(Integer userId) {
        return registry.connectionCount(userId);
    }

    private String serialize(WalletSsePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Wallet SSE payload 직렬화 실패", exception);
        }
    }
}
