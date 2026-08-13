package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionStreamPayloadTest {
    @Test
    void ENDING_시작_페이로드는_얼린_estimatedCloseTime을_endsAt으로_담는다() {
        Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = Auction.builder()
                .sellerId(1).itemId(1).auctionName("경매 A").description("설명")
                .startPrice(10_000L).deliveryFee(0L)
                .openTime(estimatedCloseTime.minus(Duration.ofDays(1)))
                .estimatedCloseTime(estimatedCloseTime).closeTime(estimatedCloseTime)
                .bidPriceUnit(1_000L).hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        auction.enterEnding(Duration.ofSeconds(90));

        AuctionStreamPayload payload = AuctionStreamPayload.endingStarted(auction, estimatedCloseTime);

        assertThat(payload.type()).isEqualTo(AuctionStreamEventType.AUCTION_ENDING_STARTED);
        assertThat(payload.auctionId()).isEqualTo(1);
        assertThat(payload.status()).isEqualTo(AuctionStatus.ENDING);
        assertThat(payload.endsAt()).isEqualTo(estimatedCloseTime);
        assertThat(payload.endsAt()).isNotEqualTo(auction.getCloseTime());
    }
}
