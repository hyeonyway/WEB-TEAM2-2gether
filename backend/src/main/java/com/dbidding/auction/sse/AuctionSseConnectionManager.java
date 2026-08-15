package com.dbidding.auction.sse;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseConnectionCloseMetrics.CloseReason;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class AuctionSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final Clock clock;
    private final SseMetrics metrics;
    private final ObjectMapper objectMapper;
    private final SseSendDispatcher sendDispatcher;
    private final ConcurrentMap<Integer, Set<SseEmitter>> emittersByAuctionId = new ConcurrentHashMap<>();
    private final ConcurrentMap<SseEmitter, Set<Integer>> auctionIdsByEmitter = new ConcurrentHashMap<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final Supplier<Number> connectionCountSupplier;

    public AuctionSseConnectionManager(
            Clock clock,
            @Qualifier("auctionSseMetrics") SseMetrics metrics,
            ObjectMapper objectMapper,
            @Qualifier("auctionSseSendDispatcher") SseSendDispatcher sendDispatcher
    ) {
        this.clock = clock;
        this.metrics = metrics;
        this.objectMapper = objectMapper;
        this.sendDispatcher = sendDispatcher;
        this.connectionCountSupplier = this::connectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
    }

    public SseEmitter connect(Set<Integer> auctionIds) {
        return register(auctionIds, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    SseEmitter register(Set<Integer> auctionIds, SseEmitter emitter) {
        Set<Integer> subscribedAuctionIds = Set.copyOf(auctionIds);
        Timer.Sample connectSample = metrics.startConnect();
        metrics.trackConnectionStart(emitter);
        auctionIdsByEmitter.put(emitter, subscribedAuctionIds);
        subscribedAuctionIds.forEach(auctionId ->
                emittersByAuctionId.computeIfAbsent(auctionId, ignored -> new CopyOnWriteArraySet<>()).add(emitter));
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
        if (send(emitter, SseEmitter.event().name("connected")
                .reconnectTime(RECONNECT_TIME_MILLIS).data("connected"))) {
            metrics.finishConnect(connectSample);
        }
        return emitter;
    }

    // #507: send용 캡이 꽉 차도 이 메서드(순회/코디네이션)는 안 묶여야 하므로, send와
    // 다른(캡 없는) executor를 쓴다 — 실제 emitter별 send는 sendDispatcher가 그대로
    // 캡이 걸릴 수 있는 auctionSseTaskExecutor에 위임한다.
    @Async("auctionSseBroadcastTaskExecutor")
    public void broadcast(AuctionStreamPayload event) {
        Set<SseEmitter> emitters = emittersByAuctionId.get(event.auctionId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        long eventId = eventSequence.incrementAndGet();
        AuctionStreamPayload publishedEvent = event.withPublishedAt(clock.instant());
        String serializedPayload = writeJson(publishedEvent);
        // emitter와 무관하게 매번 동일한 인자라 순회 전 한 번만 만들어 재사용한다 —
        // .build() 결과는 읽기 전용이라 여러 스레드가 동시에 같은 인스턴스로
        // emitter.send()해도 안전하다(serializedPayload를 이미 이렇게 재사용 중인 것과 동일).
        SseEmitter.SseEventBuilder sharedEvent = event(publishedEvent.type(), serializedPayload, eventId);
        emitters.forEach(emitter -> sendDispatcher.dispatch(() -> send(emitter, sharedEvent)));
    }

    @Async("auctionSseTaskExecutor")
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        auctionIdsByEmitter.keySet().forEach(emitter -> send(emitter,
                SseEmitter.event().comment("heartbeat")));
    }

    public int connectionCount() { return auctionIdsByEmitter.size(); }

    public void disconnectAll() {
        auctionIdsByEmitter.keySet().forEach(this::removeAndComplete);
    }

    private SseEmitter.SseEventBuilder event(
            AuctionStreamEventType eventType,
            String serializedPayload,
            long eventId
    ) {
        return SseEmitter.event().id(Long.toString(eventId))
                .name(eventType.name()).data(serializedPayload, MediaType.APPLICATION_JSON);
    }

    private String writeJson(AuctionStreamPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            log.error("event=auction.sse.payload_serialize_failed eventType={}", payload.type(), exception);
            throw new IllegalStateException("Auction SSE payload 직렬화 실패", exception);
        }
    }

    private boolean send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        Timer.Sample sample = metrics.startSend();
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            metrics.recordSendFailure();
            metrics.recordConnectionClosed(emitter, CloseReason.SEND_FAILURE);
            removeAndComplete(emitter);
            return false;
        } finally {
            metrics.finishSend(sample);
        }
        return true;
    }

    private void removeAndComplete(SseEmitter emitter) {
        remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }

    private void remove(SseEmitter emitter) {
        Set<Integer> auctionIds = auctionIdsByEmitter.remove(emitter);
        if (auctionIds == null) {
            return;
        }
        auctionIds.forEach(auctionId -> emittersByAuctionId.computeIfPresent(auctionId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        }));
    }
}
