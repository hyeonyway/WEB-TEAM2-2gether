package com.dbidding.auction.sse;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
@RequiredArgsConstructor
public class AuctionSseConnectionManager {
    private static final long CONNECTION_TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final long RECONNECT_TIME_MILLIS = 3_000L;

    private final Set<SseEmitter> emitters = new CopyOnWriteArraySet<>();
    @Qualifier("sseTaskExecutor")
    private final Executor sseTaskExecutor;

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

    public void broadcast(AuctionStreamPayload event) {
        emitters.forEach(emitter -> sseTaskExecutor.execute(() -> send(emitter,
                SseEmitter.event().name(event.type().name()).data(event))));
    }

    @Scheduled(fixedDelay = 25_000L)
    public void heartbeat() {
        emitters.forEach(emitter -> sseTaskExecutor.execute(() -> send(emitter,
                SseEmitter.event().comment("heartbeat"))));
    }

    public int connectionCount() { return emitters.size(); }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); } catch (IOException | IllegalStateException exception) { removeAndComplete(emitter); }
    }

    private void removeAndComplete(SseEmitter emitter) {
        emitters.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }
}
