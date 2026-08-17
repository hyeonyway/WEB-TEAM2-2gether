package com.dbidding.global.security.session;

import com.dbidding.sse.PerConnectionSseSendDispatcher;
import com.dbidding.sse.SseEmitterRegistry;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 로그인 유저 한 명이 여는 개인화 SSE 커넥션(알림/지갑)을 하나로 공유한다(#557). 도메인별
 * push는 여전히 {@code NotificationSseConnectionManager}/{@code WalletSseConnectionManager}가
 * 담당하지만, 실제 emitter 등록·전송 직렬화·세션 연동·heartbeat·연결 수 집계는 이 클래스가
 * 갖고 있는 registry 하나로 합쳐서, 유저당 SSE 커넥션이 3개(auction/notification/wallet)에서
 * 2개로 줄어든다.
 */
@Component
public class MeSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final SseEmitterRegistry<Integer> registry;
    private final SseSendDispatcher heartbeatDispatcher;
    // Micrometer Gauge는 이 Supplier를 약한 참조로만 들고 있어, GC되지 않도록 필드로 붙잡아둔다.
    private final Supplier<Number> connectionCountSupplier;

    @Autowired
    public MeSseConnectionManager(
            SessionSseConnectionRegistry sessionRegistry,
            @Qualifier("meSseMetrics") SseMetrics metrics,
            @Qualifier("notificationFanOutTaskExecutor") TaskExecutor heartbeatExecutor
    ) {
        this.registry = new SseEmitterRegistry<>(metrics, sessionRegistry);
        this.heartbeatDispatcher = new PerConnectionSseSendDispatcher(heartbeatExecutor);
        this.connectionCountSupplier = registry::totalConnectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
    }

    /**
     * 테스트 편의 생성자 — {@code notification.sse}/{@code wallet.sse} 패키지의 도메인
     * 매니저 테스트에서도 공유 연결 관리자를 직접 만들어 써야 해서(#557) public이다.
     */
    public MeSseConnectionManager(SseMetrics metrics, TaskExecutor heartbeatExecutor) {
        this(new SessionSseConnectionRegistry(), metrics, heartbeatExecutor);
    }

    public SseEmitter connect(Integer userId, String sessionId) {
        return register(userId, sessionId, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    public SseEmitter register(Integer userId, SseEmitter emitter) {
        return register(userId, null, emitter);
    }

    public SseEmitter register(Integer userId, String sessionId, SseEmitter emitter) {
        registry.register(Set.of(userId), emitter, sessionId);
        return emitter;
    }

    public Set<SseEmitter> emittersFor(Integer userId) {
        return registry.emittersFor(userId);
    }

    public boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event, SseMetrics callMetrics) {
        return registry.send(emitter, event, callMetrics);
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        registry.allEmitters().forEach(emitter ->
                heartbeatDispatcher.dispatch(() -> registry.send(emitter, SseEmitter.event().comment("heartbeat"))));
    }

    public int connectionCount(Integer userId) {
        return registry.connectionCount(userId);
    }

    public int totalConnectionCount() {
        return registry.totalConnectionCount();
    }
}
