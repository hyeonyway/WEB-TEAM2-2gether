package com.dbidding.wallet.sse;

import com.dbidding.global.security.session.MeSseConnectionManager;
import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
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
        // 커넥션 수 gauge는 여기서 등록하지 않는다(#560) — 알림·지갑이 커넥션을 공유하므로
        // (#557) 실제로 셀 대상은 하나뿐이고, 그 값은 MeSseConnectionManager가 이미
        // dbidding.sse.connections{stream=me} 하나로 등록한다. 여기서도 같은 값을
        // {stream=wallet}로 또 등록하면(과거엔 대시보드 호환 목적으로 그렇게 했었다)
        // 실제 연결 수가 3배로 잡혀 보이는 문제가 생긴다(#560에서 발견).
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
