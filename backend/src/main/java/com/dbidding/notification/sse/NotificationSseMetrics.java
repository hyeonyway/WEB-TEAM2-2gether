package com.dbidding.notification.sse;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class NotificationSseMetrics {
    private final MeterRegistry registry;
    private final Timer connectTimer;

    public NotificationSseMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.connectTimer = Timer.builder("dbidding.sse.connect.duration")
                .tag("stream", "notification")
                .description("SSE 연결 수립 시간")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void registerConnectionGauge(Supplier<Number> connectionCount) {
        Gauge.builder("dbidding.sse.connections", connectionCount, value -> value.get().doubleValue())
                .tag("stream", "notification")
                .description("SSE 스트림별 현재 연결 수")
                .register(registry);
    }

    public Timer.Sample startConnect() {
        return Timer.start(registry);
    }

    public void finishConnect(Timer.Sample sample) {
        sample.stop(connectTimer);
    }
}
