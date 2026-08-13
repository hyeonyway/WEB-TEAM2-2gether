package com.dbidding.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuctionTest {
    @Test
    void OPEN_경매가_ENDING으로_전환되면_closeTime만_랜덤_연장분만큼_늘고_estimatedCloseTime은_그대로다() {
        Instant closeTime = Instant.parse("2026-07-29T10:00:00Z");
        Auction auction = auction(closeTime);

        boolean transitioned = auction.enterEnding(Duration.ofSeconds(90));

        assertThat(transitioned).isTrue();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDING);
        assertThat(auction.getCloseTime()).isEqualTo(closeTime.plusSeconds(90));
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
    }

    @Test
    void 이미_ENDING인_경매를_다시_전환해도_상태와_두_시각_모두_변하지_않는다() {
        Instant closeTime = Instant.parse("2026-07-29T10:00:00Z");
        Auction auction = auction(closeTime);
        auction.enterEnding(Duration.ofMinutes(1));
        Instant extendedCloseTime = auction.getCloseTime();

        boolean transitioned = auction.enterEnding(Duration.ofMinutes(2));

        assertThat(transitioned).isFalse();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDING);
        assertThat(auction.getCloseTime()).isEqualTo(extendedCloseTime);
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
    }

    @Test
    void OPEN_상태의_일반_입찰은_마감시각을_연장하지_않는다() {
        Instant closeTime = Instant.parse("2026-07-29T10:00:00Z");
        Auction auction = auction(closeTime);

        auction.placeBid(43_000L, closeTime.minus(Duration.ofMinutes(3)));

        assertThat(auction.getCurrentPrice()).isEqualTo(43_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        assertThat(auction.getCloseTime()).isEqualTo(closeTime);
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.OPEN);
    }

    @Test
    void ENDING_상태의_일반_입찰은_현재가와_입찰수만_바꾸고_시각은_바꾸지_않는다() {
        Instant closeTime = Instant.parse("2026-07-29T10:00:00Z");
        Auction auction = auction(closeTime);
        auction.enterEnding(Duration.ofMinutes(1));
        Instant extendedCloseTime = auction.getCloseTime();

        auction.placeBid(43_000L, closeTime.minusSeconds(30));

        assertThat(auction.getCurrentPrice()).isEqualTo(43_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        assertThat(auction.getCloseTime()).isEqualTo(extendedCloseTime);
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
    }

    @Test
    void 종료_시간이_지난_경매에는_입찰할_수_없다() {
        Instant closeTime = Instant.parse("2026-07-29T10:00:00Z");
        Auction auction = auction(closeTime);

        assertThatThrownBy(() -> auction.placeBid(43_000L, closeTime))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 종료된 경매입니다.");
    }

    private Auction auction(Instant closeTime) {
        return Auction.builder()
                .sellerId(1)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minus(Duration.ofHours(1)))
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
    }
}
