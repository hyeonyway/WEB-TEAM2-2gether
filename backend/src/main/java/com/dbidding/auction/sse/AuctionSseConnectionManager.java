package com.dbidding.auction.sse;

import java.io.IOException;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.core.instrument.Timer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@Slf4j
public class AuctionSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final Clock clock;
    private final AuctionSseMetrics metrics;
    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final AtomicLong eventSequence = new AtomicLong();
    private final Supplier<Number> connectionCountSupplier;

    public AuctionSseConnectionManager(Clock clock, AuctionSseMetrics metrics) {
        this.clock = clock;
        this.metrics = metrics;
        this.connectionCountSupplier = this::connectionCount;
        metrics.registerConnectionGauge(connectionCountSupplier);
    }

    public SseEmitter connect() {
        return register(new SseEmitter(CONNECTION_TIMEOUT_MILLIS));
    }

    SseEmitter register(SseEmitter emitter) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> removeAndComplete(emitter));
        emitter.onError(error -> removeAndComplete(emitter));
        send(emitter, SseEmitter.event().name("connected")
                .reconnectTime(RECONNECT_TIME_MILLIS).data("connected"));
        return emitter;
    }

    @Async("auctionSseTaskExecutor")
    public void broadcast(AuctionStreamPayload event) {
        long eventId = eventSequence.incrementAndGet();
        AuctionStreamPayload publishedEvent = event.withPublishedAt(clock.instant());
        emitters.forEach(emitter -> send(emitter, event(publishedEvent, eventId)));
    }

    @Async("auctionSseTaskExecutor")
    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emitters.forEach(emitter -> send(emitter,
                SseEmitter.event().comment("heartbeat")));
    }

    public int connectionCount() { return emitters.size(); }

    public void disconnectAll() {
        emitters.forEach(this::removeAndComplete);
    }

    private SseEmitter.SseEventBuilder event(AuctionStreamPayload payload, long eventId) {
        return SseEmitter.event().id(Long.toString(eventId))
                .name(payload.type().name()).data(payload);
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        Timer.Sample sample = metrics.startSend();
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException exception) {
            metrics.recordSendFailure();
            removeAndComplete(emitter);
        } finally {
            metrics.finishSend(sample);
        }
    }

    private void removeAndComplete(SseEmitter emitter) {
        emitters.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }
}
