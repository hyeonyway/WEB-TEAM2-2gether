package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuctionCloseRequestedStreamEventTest {
    @Test
    void 경매_종료_요청은_schema와_필수필드_누락을_거부한다() {
        assertThatThrownBy(() -> AuctionWalletTimelineEvent.from("1-0", Map.of(
                "schemaVersion", "2", "eventType", "auction.close-requested.v1")))
                .isInstanceOf(InvalidBidStreamEventException.class);
        assertThatThrownBy(() -> AuctionWalletTimelineEvent.from("1-0", Map.of(
                "schemaVersion", "1", "eventType", "auction.close-requested.v1", "auctionId", "not-number", "occurredAt", "bad")))
                .isInstanceOf(InvalidBidStreamEventException.class);
    }
}
