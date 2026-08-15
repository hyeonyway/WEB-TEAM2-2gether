package com.dbidding.sse.metrics;

import com.dbidding.sse.metrics.SseConnectionCloseMetrics.CloseReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Auction/Notification/Wallet SSE가 공통으로 쓰는 메트릭 배선(#508). {@code stream}
 * 값으로 도메인별 metric 이름/태그를 지금까지와 동일하게 생성한다 — 대시보드가
 * 참조하는 이름은 바뀌지 않는다({@code dbidding.<stream>.sse.send.duration}/
 * {@code .send.failures}, {@code dbidding.sse.connect.duration}/{@code .connections}는
 * {@code tag("stream", stream)}로 구분).
 */
public class SseMetrics {
    private final MeterRegistry registry;
    private final String stream;
    private final Timer sendTimer;
    private final Timer connectTimer;
    private final Counter sendFailures;
    private final SseConnectionCloseMetrics closeMetrics;

    public SseMetrics(MeterRegistry registry, String stream) {
        this(registry, stream, Clock.systemUTC());
    }

    public SseMetrics(MeterRegistry registry, String stream, Clock clock) {
        this.registry = registry;
        this.stream = stream;
        this.sendTimer = Timer.builder("dbidding." + stream + ".sse.send.duration")
                .description(stream + " SSE emitter 전송시간")
                .publishPercentileHistogram()
                .register(registry);
        this.connectTimer = Timer.builder("dbidding.sse.connect.duration")
                .tag("stream", stream)
                .description("SSE 연결 수립 시간")
                .publishPercentileHistogram()
                .register(registry);
        this.sendFailures = Counter.builder("dbidding." + stream + ".sse.send.failures")
                .description(stream + " SSE emitter 전송 실패 건수")
                .register(registry);
        this.closeMetrics = new SseConnectionCloseMetrics(registry, stream, clock);
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
                .tag("stream", stream)
                .description("SSE 스트림별 현재 연결 수")
                .register(registry);
    }

    public void trackConnectionStart(SseEmitter emitter) {
        closeMetrics.trackStart(emitter);
    }

    public void recordConnectionClosed(SseEmitter emitter, CloseReason reason) {
        closeMetrics.recordClose(emitter, reason);
    }
}
