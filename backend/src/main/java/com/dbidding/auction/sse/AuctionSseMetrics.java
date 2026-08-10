package com.dbidding.auction.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AuctionSseMetrics {
    private final MeterRegistry registry;
    private final Timer sendTimer;
    private final Timer connectTimer;
    private final Counter sendFailures;

    public AuctionSseMetrics(MeterRegistry registry) {
        this.registry = registry;
        sendTimer = Timer.builder("dbidding.auction.sse.send.duration")
                .description("경매 SSE emitter 전송시간")
                .publishPercentileHistogram()
                .register(registry);
        connectTimer = Timer.builder("dbidding.sse.connect.duration")
                .tag("stream", "auction")
                .description("SSE 연결 수립 시간")
                .publishPercentileHistogram()
                .register(registry);
        sendFailures = Counter.builder("dbidding.auction.sse.send.failures")
                .description("경매 SSE emitter 전송 실패 건수")
                .register(registry);
    }

    public Timer.Sample startSend() {
        return Timer.start(registry);
    }

    public void finishSend(Timer.Sample sample) {
        sample.stop(sendTimer);
    }

    public void recordSendFailure() {
        sendFailures.increment();
    }

    public Timer.Sample startConnect() {
        return Timer.start(registry);
    }

    public void finishConnect(Timer.Sample sample) {
        sample.stop(connectTimer);
    }

    public void registerConnectionGauge(Supplier<Number> connectionCount) {
        Gauge.builder("dbidding.sse.connections", connectionCount, value -> value.get().doubleValue())
                .tag("stream", "auction")
                .description("SSE 스트림별 현재 연결 수")
                .register(registry);
    }
}
