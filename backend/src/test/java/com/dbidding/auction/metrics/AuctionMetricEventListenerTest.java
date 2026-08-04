package com.dbidding.auction.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionMetricEventListenerTest {

    @Test
    void 커밋된_마감시간_연장_이벤트만_지표로_기록한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AuctionMetrics metrics = new AuctionMetrics(registry);
        AuctionMetricEventListener listener = new AuctionMetricEventListener(metrics);

        listener.recordExtension(new AuctionCloseScheduleChangedEvent(
                1,
                LocalDateTime.of(2026, 8, 4, 12, 0),
                "close_time_extended"
        ));
        listener.recordExtension(new AuctionCloseScheduleChangedEvent(
                2,
                LocalDateTime.of(2026, 8, 4, 13, 0),
                "auction_created"
        ));

        assertThat(registry.get("dbidding.auction.extensions")
                .counter()
                .count()).isEqualTo(1);
    }
}
