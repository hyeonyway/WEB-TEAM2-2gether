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
    void 입찰_핵심구간과_하위단계와_flush_시간을_기록한다() {
        Timer.Sample criticalSection = metrics.startBidCriticalSection();
        Timer.Sample flush = metrics.startBidFlush();

        metrics.recordBidStep(AuctionMetrics.BidStep.OUTBID, () -> { });
        metrics.recordBidStep(AuctionMetrics.BidStep.HOLD, () -> { });
        metrics.recordBidStep(AuctionMetrics.BidStep.SAVE, () -> { });
        metrics.finishBidFlush(flush);
        metrics.finishBidCriticalSection(criticalSection);

        assertThat(registry.get("dbidding.bid.critical_section.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.bid.step.duration").tag("step", "outbid").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.bid.step.duration").tag("step", "hold").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.bid.step.duration").tag("step", "save").timer().count()).isEqualTo(1);
        assertThat(registry.get("dbidding.bid.db_flush.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void 경매_연장을_카운트한다() {
        metrics.recordExtension();

        assertThat(registry.get("dbidding.auction.extensions")
                .counter()
                .count()).isEqualTo(1);
    }

    @Test
    void ENDING_전환을_기록하면_전용_카운터가_증가한다() {
        metrics.recordEndingTransition();

        assertThat(registry.get("dbidding.auction.ending.transitions").counter().count()).isEqualTo(1);
    }
}
