package com.dbidding.global.security.session;

import com.dbidding.sse.metrics.SseMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** #557 — {@code MeSseConnectionManager}(알림·지갑 통합 SSE)의 메트릭 배선. */
@Configuration
public class MeSseConfig {
    @Bean(name = "meSseMetrics")
    public SseMetrics meSseMetrics(MeterRegistry meterRegistry, Clock clock) {
        return new SseMetrics(meterRegistry, "me", clock);
    }
}
