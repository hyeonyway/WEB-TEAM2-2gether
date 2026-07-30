package com.dbidding.auction.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionTest {
    @Test
    void 마감_시간_근처에_입찰하면_종료_시간을_연장한다() {
        LocalDateTime closeTime = LocalDateTime.of(2026, 7, 29, 10, 0);
        Auction auction = auction(closeTime);

        boolean extended = auction.placeBid(
                43_000L,
                closeTime.minusMinutes(3),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );

        assertThat(extended).isTrue();
        assertThat(auction.getCurrentPrice()).isEqualTo(43_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        assertThat(auction.getCloseTime()).isEqualTo(closeTime.plusMinutes(5));
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime.plusMinutes(5));
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDING);
    }

    @Test
    void 마감_시간_근처가_아닌_입찰은_종료_시간을_연장하지_않는다() {
        LocalDateTime closeTime = LocalDateTime.of(2026, 7, 29, 10, 0);
        Auction auction = auction(closeTime);

        boolean extended = auction.placeBid(
                43_000L,
                closeTime.minusMinutes(10),
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        );

        assertThat(extended).isFalse();
        assertThat(auction.getCloseTime()).isEqualTo(closeTime);
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.OPEN);
    }

    @Test
    void 종료_시간이_지난_경매에는_입찰할_수_없다() {
        LocalDateTime closeTime = LocalDateTime.of(2026, 7, 29, 10, 0);
        Auction auction = auction(closeTime);

        assertThatThrownBy(() -> auction.placeBid(
                43_000L,
                closeTime,
                Duration.ofMinutes(5),
                Duration.ofMinutes(5)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 종료된 경매입니다.");
    }

    private Auction auction(LocalDateTime closeTime) {
        return Auction.builder()
                .sellerId(1)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minusHours(1))
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
    }
}
