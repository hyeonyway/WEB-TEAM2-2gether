package com.dbidding.wallet.sse;

import com.dbidding.sse.metrics.SseConnectionCloseMetrics;
import com.dbidding.sse.metrics.SseConnectionCloseMetrics.CloseReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class WalletSseMetrics {
    private final MeterRegistry registry;
    private final Timer connectTimer;
    private final Counter sendFailures;
    private final SseConnectionCloseMetrics closeMetrics;

    public WalletSseMetrics(MeterRegistry registry) {
        this(registry, Clock.systemUTC());
    }

    @Autowired
    public WalletSseMetrics(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.connectTimer = Timer.builder("dbidding.sse.connect.duration")
                .tag("stream", "wallet")
                .description("SSE 연결 수립 시간")
                .publishPercentileHistogram()
                .register(registry);
        this.sendFailures = Counter.builder("dbidding.wallet.sse.send.failures")
                .description("지갑 SSE emitter 전송 실패 건수")
                .register(registry);
        this.closeMetrics = new SseConnectionCloseMetrics(registry, "wallet", clock);
    }

    public void registerConnectionGauge(Supplier<Number> connectionCount) {
        Gauge.builder("dbidding.sse.connections", connectionCount, value -> value.get().doubleValue())
                .tag("stream", "wallet")
                .description("SSE 스트림별 현재 연결 수")
                .register(registry);
    }

    public Timer.Sample startConnect() {
        return Timer.start(registry);
    }

    public void finishConnect(Timer.Sample sample) {
        sample.stop(connectTimer);
    }

    public void recordSendFailure() {
        sendFailures.increment();
    }

    public void trackConnectionStart(SseEmitter emitter) {
        closeMetrics.trackStart(emitter);
    }

    public void recordConnectionClosed(SseEmitter emitter, CloseReason reason) {
        closeMetrics.recordClose(emitter, reason);
    }
}
