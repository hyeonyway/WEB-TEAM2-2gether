package com.dbidding.sse.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결의 종료 원인별 횟수와 지속 시간을 계측한다. emitter별 연결 시작 시각을
 * 보관하고, {@link #recordClose}는 emitter당 최초 1회만 반영되도록 멱등하게 동작한다
 * (onError 뒤 onCompletion이 연속 호출되거나, send 실패 뒤 completion callback이
 * 뒤따르는 경우를 포함).
 */
public class SseConnectionCloseMetrics {

    public enum CloseReason {
        COMPLETION("completion"),
        TIMEOUT("timeout"),
        ERROR("error"),
        SEND_FAILURE("send_failure");

        private final String tagValue;

        CloseReason(String tagValue) {
            this.tagValue = tagValue;
        }
    }

    private final Clock clock;
    private final ConcurrentHashMap<SseEmitter, Instant> connectedAt = new ConcurrentHashMap<>();
    private final Map<CloseReason, Counter> closedCounters = new EnumMap<>(CloseReason.class);
    private final Map<CloseReason, Timer> durationTimers = new EnumMap<>(CloseReason.class);

    public SseConnectionCloseMetrics(MeterRegistry registry, String stream, Clock clock) {
        this.clock = clock;
        for (CloseReason reason : CloseReason.values()) {
            closedCounters.put(reason, Counter.builder("dbidding.sse.connections.closed")
                    .tag("stream", stream)
                    .tag("reason", reason.tagValue)
                    .description("종료된 SSE 연결의 누적 수")
                    .register(registry));
            durationTimers.put(reason, Timer.builder("dbidding.sse.connection.duration")
                    .tag("stream", stream)
                    .tag("reason", reason.tagValue)
                    .description("연결 수립부터 제거까지의 지속 시간")
                    .publishPercentileHistogram()
                    .register(registry));
        }
    }

    public void trackStart(SseEmitter emitter) {
        connectedAt.put(emitter, clock.instant());
    }

    public void recordClose(SseEmitter emitter, CloseReason reason) {
        Instant startedAt = connectedAt.remove(emitter);
        if (startedAt == null) {
            return;
        }
        closedCounters.get(reason).increment();
        durationTimers.get(reason).record(Duration.between(startedAt, clock.instant()));
    }
}
