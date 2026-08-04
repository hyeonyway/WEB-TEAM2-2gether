package com.dbidding.auction.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuctionMetricsTest {

    private SimpleMeterRegistry registry;
    private AuctionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new AuctionMetrics(registry);
    }

    @Test
    void 입찰_처리시간을_결과별로_기록한다() {
        Timer.Sample sample = metrics.start();

        metrics.finishBid(sample, AuctionMetrics.BidResult.ACCEPTED);

        assertThat(registry.get("dbidding.bid.duration")
                .tag("result", "accepted")
                .timer()
                .count()).isEqualTo(1);
        assertThat(registry.get("dbidding.bid.duration")
                .tag("result", "rejected")
                .timer()
                .count()).isZero();
    }

    @Test
    void 경매_락_대기시간을_작업별로_기록한다() {
        Timer.Sample sample = metrics.start();

        metrics.finishAuctionLockWait(sample, AuctionMetrics.LockOperation.BID);

        assertThat(registry.get("dbidding.auction.lock.wait")
                .tag("operation", "bid")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void 경매_연장을_카운트한다() {
        metrics.recordExtension();

        assertThat(registry.get("dbidding.auction.extensions")
                .counter()
                .count()).isEqualTo(1);
    }
}
