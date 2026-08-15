package com.dbidding.auction.sse;

import com.dbidding.sse.SseEmitterRegistry;
import com.dbidding.sse.SseSendDispatcher;
import com.dbidding.sse.metrics.SseMetrics;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class AuctionSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final Clock clock;
    private final SseEmitterRegistry<Integer> registry;
    private final ObjectMapper objectMapper;
    private final SseSendDispatcher sendDispatcher;
    private final AtomicLong eventSequence = new AtomicLong();
    // Micrometer Gauge는 이 Supplier를 약한 참조로만 들고 있어, GC되지 않도록 필드로 붙잡아둔다.
    private final Supplier<Number> connectionCountSupplier;

    public AuctionSseConnectionManager(
            Clock clock,
            @Qualifier("auctionSseMetrics") SseMetrics metrics,
            ObjectMapper objectMapper,
            @Qualifier("auctionSseSendDispatcher") SseSendDispatcher sendDispatcher
    ) {
        this.clock = clock;
        this.registry = new SseEmitterRegistry<>(metrics);
        this.objectMapper = objectMapper;
        this.sendDispatcher = sendDispatcher;
        this.connectionCountSupplier = registry::totalConnectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
    }

    public SseEmitter connect(Set<Integer> auctionIds) {
        return register(auctionIds, new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    SseEmitter register(Set<Integer> auctionIds, SseEmitter emitter) {
        registry.register(auctionIds, emitter, null);
        return emitter;
    }

    // #507: send용 캡이 꽉 차도 이 메서드(순회/코디네이션)는 안 묶여야 하므로, send와
    // 다른(캡 없는) executor를 쓴다 — 실제 emitter별 send는 sendDispatcher가 그대로
    // 캡이 걸릴 수 있는 auctionSseTaskExecutor에 위임한다.
    @Async("auctionSseBroadcastTaskExecutor")
    public void broadcast(AuctionStreamPayload event) {
        Set<SseEmitter> emitters = registry.emittersFor(event.auctionId());
        if (emitters.isEmpty()) {
            return;
        }
        long eventId = eventSequence.incrementAndGet();
        AuctionStreamPayload publishedEvent = event.withPublishedAt(clock.instant());
        String serializedPayload = writeJson(publishedEvent);
        AuctionStreamEventType eventType = publishedEvent.type();
        // serializedPayload/eventId/eventType은 emitter와 무관하게 동일해 순회 전 한 번만
        // 만들어 재사용해도 되지만, SseEmitter.SseEventBuilder 인스턴스 자체는 재사용하면
        // 안 된다 — Spring의 SseEventBuilderImpl.build()는 매 호출마다 내부 LinkedHashSet에
        // add하는 구조라(캐싱 안 됨), 같은 인스턴스를 여러 emitter가 동시에 send()하면
        // 그 내부 Set에 여러 스레드가 동시에 add하다 ConcurrentModificationException이 난다.
        // 그래서 emitter마다 event(...)를 새로 호출해 독립된 builder를 만든다.
        emitters.forEach(emitter -> sendDispatcher.dispatch(() -> registry.send(emitter, event(eventType, serializedPayload, eventId))));
    }

    @Async("auctionSseTaskExecutor")
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        registry.heartbeatAll();
    }

    public int connectionCount() {
        return registry.totalConnectionCount();
    }

    public void disconnectAll() {
        registry.disconnectAll();
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
}
