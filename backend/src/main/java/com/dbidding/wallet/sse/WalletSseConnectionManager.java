package com.dbidding.wallet.sse;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WalletSseConnectionManager {
    public static final String WALLET_STATE_CHANGED = "wallet-state-changed";

    private final MeSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;
    private final SseMetrics metrics;
    private final SseSendDispatcher sendDispatcher;
    // Micrometer Gauge는 이 Supplier를 약한 참조로만 들고 있어, GC되지 않도록 필드로 붙잡아둔다.
    private final Supplier<Number> connectionCountSupplier;

    public WalletSseConnectionManager(
            MeSseConnectionManager connectionManager,
            ObjectMapper objectMapper,
            @Qualifier("walletSseTaskExecutor") TaskExecutor sendExecutor,
            @Qualifier("walletSseMetrics") SseMetrics metrics
    ) {
        this.connectionManager = connectionManager;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.sendDispatcher = new PerConnectionSseSendDispatcher(sendExecutor);
        // 알림·지갑이 이제 커넥션을 공유하므로(#557) 이 값은 공유 커넥션 수와 같다 — 기존
        // dbidding.sse.connections{stream=wallet} 대시보드/알림이 계속 값을 받게 유지한다.
        this.connectionCountSupplier = connectionManager::totalConnectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
    }

    public void push(Integer userId, WalletSsePayload payload) {
        Set<SseEmitter> emitters = connectionManager.emittersFor(userId);
        if (emitters.isEmpty()) {
            return;
        }
        String serialized = serialize(payload);
        emitters.forEach(emitter -> sendDispatcher.dispatch(() -> connectionManager.send(
                emitter,
                SseEmitter.event().name(WALLET_STATE_CHANGED).data(serialized, MediaType.APPLICATION_JSON),
                metrics
        )));
    }

    public int connectionCount(Integer userId) {
        return connectionManager.connectionCount(userId);
    }

    public int totalConnectionCount() {
        return connectionManager.totalConnectionCount();
    }

    private String serialize(WalletSsePayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Wallet SSE payload 직렬화 실패", exception);
        }
    }
}
