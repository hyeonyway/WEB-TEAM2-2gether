package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class AuctionSseMetricsTest {

    @Test
    void SSE_전송시간과_실패건수를_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionSseMetrics metrics = new AuctionSseMetrics(registry);
        Timer.Sample sample = metrics.startSend();

        metrics.finishSend(sample);
        metrics.recordSendFailure();

        assertThat(registry.get("dbidding.auction.sse.send.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.auction.sse.send.failures").counter().count()).isEqualTo(1);
    }
}
