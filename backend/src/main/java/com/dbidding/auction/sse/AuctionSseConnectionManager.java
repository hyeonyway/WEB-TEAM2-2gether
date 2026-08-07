package com.dbidding.auction.sse;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
    private final Map<Integer, ReplayEvent> latestEventsByAuction = new ConcurrentHashMap<>();
    private final AtomicLong latestDiscardedEventId = new AtomicLong();

    @Value("${AUCTION_SSE_REPLAY_STATE_CAPACITY:1000}")
    private int replayStateCapacity = 1000;

    public SseEmitter connect(String lastEventId) {
        return register(new SseEmitter(CONNECTION_TIMEOUT_MILLIS), parseLastEventId(lastEventId));
    }

    SseEmitter register(SseEmitter emitter) {
        return register(emitter, null);
    }

    SseEmitter register(SseEmitter emitter, Long lastEventId) {
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> removeAndComplete(emitter));
        emitter.onError(error -> removeAndComplete(emitter));
        send(emitter, SseEmitter.event().name("connected")
                .reconnectTime(RECONNECT_TIME_MILLIS).data("connected"));
        replay(emitter, lastEventId);
        return emitter;
    }

    @Async("auctionSseTaskExecutor")
    public void broadcast(AuctionStreamPayload event) {
        ReplayEvent replayEvent = append(event);
        emitters.forEach(emitter -> send(emitter, event(replayEvent)));
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

    private ReplayEvent append(AuctionStreamPayload payload) {
        ReplayEvent event = new ReplayEvent(eventSequence.incrementAndGet(), payload);
        latestEventsByAuction.put(payload.auctionId(), event);
        if (latestEventsByAuction.size() > replayStateCapacity) {
            latestEventsByAuction.entrySet().stream()
                    .min(Map.Entry.comparingByValue(Comparator.comparingLong(ReplayEvent::id)))
                    .ifPresent(discarded -> {
                        if (latestEventsByAuction.remove(discarded.getKey(), discarded.getValue())) {
                            latestDiscardedEventId.accumulateAndGet(discarded.getValue().id(), Math::max);
                        }
                    });
        }
        return event;
    }

    private void replay(SseEmitter emitter, Long lastEventId) {
        if (lastEventId == null || latestEventsByAuction.isEmpty()) {
            return;
        }
        if (lastEventId < latestDiscardedEventId.get()) {
            send(emitter, SseEmitter.event().name("replay-reset")
                    .data("Replay window expired. Refresh auction state."));
        }
        latestEventsByAuction.values().stream()
                .filter(event -> event.id() > lastEventId)
                .sorted((left, right) -> Long.compare(left.id(), right.id()))
                .forEach(event -> send(emitter, event(event)));
    }

    private SseEmitter.SseEventBuilder event(ReplayEvent replayEvent) {
        return SseEmitter.event().id(Long.toString(replayEvent.id()))
                .name(replayEvent.payload().type().name()).data(replayEvent.payload());
    }

    private Long parseLastEventId(String lastEventId) {
        if (lastEventId == null || lastEventId.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(lastEventId);
        } catch (NumberFormatException exception) {
            log.warn("event=auction.sse.replay.invalid_last_event_id value={}", lastEventId);
            return null;
        }
    }

    private void send(SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try { emitter.send(event); } catch (IOException | IllegalStateException exception) { removeAndComplete(emitter); }
    }

    private void removeAndComplete(SseEmitter emitter) {
        emitters.remove(emitter);
        try { emitter.complete(); } catch (IllegalStateException ignored) { }
    }

    private record ReplayEvent(long id, AuctionStreamPayload payload) { }
}
