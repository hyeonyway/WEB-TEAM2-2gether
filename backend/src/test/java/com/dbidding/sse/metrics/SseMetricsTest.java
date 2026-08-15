package com.dbidding.sse.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SseMetricsTest {

    @ParameterizedTest
    @ValueSource(strings = {"auction", "notification", "wallet"})
    void SSE_전송시간과_실패건수를_stream별_이름으로_기록한다(String stream) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseMetrics metrics = new SseMetrics(registry, stream);
        Timer.Sample sample = metrics.startSend();

        metrics.finishSend(sample);
        metrics.recordSendFailure();

        assertThat(registry.get("dbidding." + stream + ".sse.send.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding." + stream + ".sse.send.failures").counter().count()).isEqualTo(1);
    }

    @Test
    void 연결수립_시간과_연결수_gauge는_stream_태그로_구분한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SseMetrics auction = new SseMetrics(registry, "auction");
        SseMetrics notification = new SseMetrics(registry, "notification");
        auction.registerConnectionGauge(() -> 1);
        notification.registerConnectionGauge(() -> 2);

        Timer.Sample sample = auction.startConnect();
        auction.finishConnect(sample);

        assertThat(registry.get("dbidding.sse.connect.duration").tag("stream", "auction").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.connections").tag("stream", "auction").gauge().value()).isEqualTo(1);
        assertThat(registry.get("dbidding.sse.connections").tag("stream", "notification").gauge().value()).isEqualTo(2);
    }
}
