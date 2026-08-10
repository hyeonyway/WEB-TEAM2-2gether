package com.dbidding.notification.sse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class NotificationSseMetrics {
    private final MeterRegistry registry;

    public NotificationSseMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void registerConnectionGauge(Supplier<Number> connectionCount) {
        Gauge.builder("dbidding.sse.connections", connectionCount, value -> value.get().doubleValue())
                .tag("stream", "notification")
                .description("SSE 스트림별 현재 연결 수")
                .register(registry);
    }
}
