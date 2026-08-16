package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuctionEndingStartedStreamEventTest {
    @Test
    void ending_started_Stream은_실제_마감시각을_가진_전용_이벤트로_파싱된다() {
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from("1-0", Map.of(
                "schemaVersion", "1", "eventType", "auction.ending-started.v1", "auctionId", "7",
                "closeTime", "2026-08-10T00:06:30Z", "closeTimeEpochMillis", "1786320390000",
                "occurredAt", "2026-08-10T00:00:00Z"
        ));

        assertThat(event).isInstanceOf(AuctionEndingStartedStreamEvent.class);
        AuctionEndingStartedStreamEvent ending = (AuctionEndingStartedStreamEvent) event;
        assertThat(ending.auctionId()).isEqualTo(7);
        assertThat(ending.closeTime()).isEqualTo(Instant.parse("2026-08-10T00:06:30Z"));
        assertThat(ending.archiveEventType()).isEqualTo("auction.ending-started.v1");
    }

    @Test
    void ending_started_Stream은_schema와_필수시각이_없으면_거부된다() {
        assertThatThrownBy(() -> AuctionWalletTimelineEvent.from("1-0", Map.of(
                "schemaVersion", "2", "eventType", "auction.ending-started.v1")))
                .isInstanceOf(InvalidBidStreamEventException.class);
        assertThatThrownBy(() -> AuctionWalletTimelineEvent.from("1-0", Map.of(
                "schemaVersion", "1", "eventType", "auction.ending-started.v1", "auctionId", "7", "occurredAt", "2026-08-10T00:00:00Z")))
                .isInstanceOf(InvalidBidStreamEventException.class);
    }
}
