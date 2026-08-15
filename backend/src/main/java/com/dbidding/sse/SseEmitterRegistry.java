package com.dbidding.sse;

import com.dbidding.global.security.session.SessionSseConnectionRegistry;
import com.dbidding.sse.metrics.SseConnectionCloseMetrics.CloseReason;
import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.Timer;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Auction/Notification/Wallet SSE가 공통으로 쓰는 등록/제거/전송/heartbeat 로직(#508).
 * emitter 1개가 키(topic) 여러 개에 동시에 구독할 수 있도록 {@code Set<K>}로 일반화했다
 * (Auction은 auctionId 여러 개, Notification/Wallet은 {@code Set.of(userId)} 하나).
 * 세션 연동은 옵션이다 — {@code sessionRegistry}가 {@code null}이면(Auction) 세션 관련
 * 동작을 전부 건너뛴다.
 */
public class SseEmitterRegistry<K> {
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final ConcurrentMap<K, Set<SseEmitter>> emittersByKey = new ConcurrentHashMap<>();
    private final ConcurrentMap<SseEmitter, Set<K>> keysByEmitter = new ConcurrentHashMap<>();
    private final ConcurrentMap<SseEmitter, String> sessionIdByEmitter = new ConcurrentHashMap<>();
    // emitter 1개가 키(topic) 여러 개를 동시에 구독할 수 있어서, 서로 다른 키의 broadcast가
    // 같은 emitter에 대해 동시에 send()를 호출할 수 있다. SseEmitter.send()는 동시 호출을
    // 지원하지 않아 IllegalStateException으로 연결이 끊기므로, emitter별로 send를 직렬화한다.
    // 가상스레드 환경(auctionSseTaskExecutor 등)이라 synchronized 대신 ReentrantLock을 쓴다 —
    // synchronized는 JDK 21에서 블로킹 시 가상스레드를 캐리어에 pinning시킨다.
    private final ConcurrentMap<SseEmitter, ReentrantLock> sendLocksByEmitter = new ConcurrentHashMap<>();
    private final SseMetrics metrics;
    private final SessionSseConnectionRegistry sessionRegistry;

    public SseEmitterRegistry(SseMetrics metrics) {
        this(metrics, null);
    }

    public SseEmitterRegistry(SseMetrics metrics, SessionSseConnectionRegistry sessionRegistry) {
        this.metrics = metrics;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * emitter를 {@code keys} 전원에 등록하고 "connected" 이벤트를 보낸다. 세션이 이미 종료된
     * 상태라면(세션 레지스트리가 있을 때만 해당) 등록을 취소하고 {@code false}를 반환한다 —
     * 이 경우에도 emitter 자체는 {@link SessionSseConnectionRegistry#register}가 이미 완료 처리한다.
     */
    public boolean register(Set<K> keys, SseEmitter emitter, String sessionId) {
        Set<K> subscribedKeys = Set.copyOf(keys);
        Timer.Sample connectSample = metrics.startConnect();
        metrics.trackConnectionStart(emitter);
        keysByEmitter.put(emitter, subscribedKeys);
        subscribedKeys.forEach(key ->
                emittersByKey.computeIfAbsent(key, ignored -> new CopyOnWriteArraySet<>()).add(emitter));
        if (sessionId != null) {
            sessionIdByEmitter.put(emitter, sessionId);
        }
        emitter.onCompletion(() -> {
            metrics.recordConnectionClosed(emitter, CloseReason.COMPLETION);
            remove(emitter);
        });
        emitter.onTimeout(() -> {
            metrics.recordConnectionClosed(emitter, CloseReason.TIMEOUT);
            removeAndComplete(emitter);
        });
        emitter.onError(error -> {
            metrics.recordConnectionClosed(emitter, CloseReason.ERROR);
            removeAndComplete(emitter);
        });
        if (sessionId != null && sessionRegistry != null && !sessionRegistry.register(sessionId, emitter)) {
            remove(emitter);
            return false;
        }
        if (send(emitter, SseEmitter.event().name("connected").reconnectTime(RECONNECT_TIME_MILLIS).data("connected"))) {
            metrics.finishConnect(connectSample);
        }
        return true;
    }

    public Set<SseEmitter> emittersFor(K key) {
        Set<SseEmitter> emitters = emittersByKey.get(key);
        return emitters == null ? Set.of() : emitters;
    }

    /** 등록된 emitter 전체(키 무관). 도메인이 직접 순회하며 자기만의 dispatch 전략을 쓰고 싶을 때 사용한다. */
    public Set<SseEmitter> allEmitters() {
        return Set.copyOf(keysByEmitter.keySet());
    }

    public boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        Timer.Sample sample = metrics.startSend();
        ReentrantLock sendLock = sendLocksByEmitter.computeIfAbsent(emitter, ignored -> new ReentrantLock());
        sendLock.lock();
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            metrics.recordSendFailure();
            metrics.recordConnectionClosed(emitter, CloseReason.SEND_FAILURE);
            removeAndComplete(emitter);
            return false;
        } finally {
            sendLock.unlock();
            metrics.finishSend(sample);
        }
        return true;
    }

    /** 등록된 emitter 전원에게 heartbeat 주석 이벤트를 보낸다. */
    public void heartbeatAll() {
        keysByEmitter.keySet().forEach(emitter -> send(emitter, SseEmitter.event().comment("heartbeat")));
    }

    public void removeAndComplete(SseEmitter emitter) {
        remove(emitter);
        try {
            emitter.complete();
        } catch (IllegalStateException ignored) {
            // 이미 완료된 연결은 제거만 보장한다.
        }
    }

    public void remove(SseEmitter emitter) {
        Set<K> keys = keysByEmitter.remove(emitter);
        String sessionId = sessionIdByEmitter.remove(emitter);
        sendLocksByEmitter.remove(emitter);
        if (sessionId != null && sessionRegistry != null) {
            sessionRegistry.unregister(sessionId, emitter);
        }
        if (keys == null) {
            return;
        }
        keys.forEach(key -> emittersByKey.computeIfPresent(key, (ignored, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        }));
    }

    public int connectionCount(K key) {
        Set<SseEmitter> emitters = emittersByKey.get(key);
        return emitters == null ? 0 : emitters.size();
    }

    public int totalConnectionCount() {
        return keysByEmitter.size();
    }

    /** 등록된 emitter 전원을 완료 처리하고 제거한다(테스트/운영 도구용). */
    public void disconnectAll() {
        keysByEmitter.keySet().forEach(this::removeAndComplete);
    }
}
