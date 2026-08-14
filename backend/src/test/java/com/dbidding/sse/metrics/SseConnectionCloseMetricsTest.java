package com.dbidding.sse.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.dbidding.sse.metrics.SseConnectionCloseMetrics.CloseReason;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseConnectionCloseMetricsTest {

    @Test
    void 종료_원인별로_카운터와_지속시간을_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant start = Instant.parse("2026-08-14T00:00:00Z");
        Clock clock = Clock.fixed(start, ZoneOffset.UTC);
        SseConnectionCloseMetrics metrics = new SseConnectionCloseMetrics(registry, "auction", clock);
        SseEmitter emitter = mock(SseEmitter.class);

        metrics.trackStart(emitter);
        metrics.recordClose(emitter, CloseReason.TIMEOUT);

        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "auction").tag("reason", "timeout").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "auction").tag("reason", "completion").counter().count()).isZero();
    }

    @Test
    void 같은_emitter에_대해_두번째_종료_기록은_무시한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseConnectionCloseMetrics metrics = new SseConnectionCloseMetrics(registry, "auction", Clock.systemUTC());
        SseEmitter emitter = mock(SseEmitter.class);

        metrics.trackStart(emitter);
        metrics.recordClose(emitter, CloseReason.SEND_FAILURE);
        metrics.recordClose(emitter, CloseReason.COMPLETION);

        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "auction").tag("reason", "send_failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.connections.closed")
                .tag("stream", "auction").tag("reason", "completion").counter().count()).isZero();
    }

    @Test
    void 연결_지속_시간을_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant start = Instant.parse("2026-08-14T00:00:00Z");
        java.util.concurrent.atomic.AtomicReference<Instant> now = new java.util.concurrent.atomic.AtomicReference<>(start);
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() { return ZoneOffset.UTC; }

            @Override
            public Clock withZone(java.time.ZoneId zone) { return this; }

            @Override
            public Instant instant() { return now.get(); }
        };
        SseConnectionCloseMetrics metrics = new SseConnectionCloseMetrics(registry, "wallet", clock);
        SseEmitter emitter = mock(SseEmitter.class);

        metrics.trackStart(emitter);
        now.set(start.plus(Duration.ofSeconds(5)));
        metrics.recordClose(emitter, CloseReason.ERROR);

        assertThat(registry.get("dbidding.sse.connection.duration")
                .tag("stream", "wallet").tag("reason", "error").timer().totalTime(java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(5.0);
    }
}
