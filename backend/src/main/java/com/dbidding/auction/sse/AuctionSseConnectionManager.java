package com.dbidding.auction.sse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    private final AtomicLong eventSequence = new AtomicLong();
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
        emitters.forEach(emitter -> send(emitter, event(event, eventId)));
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
        try { emitter.send(event); } catch (IOException | IllegalStateException exception) { removeAndComplete(emitter); }
    }

    private void removeAndComplete(SseEmitter emitter) {
        emitters.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }
}
