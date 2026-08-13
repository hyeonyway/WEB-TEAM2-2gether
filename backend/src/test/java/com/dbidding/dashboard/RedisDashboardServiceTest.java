package com.dbidding.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.auction.service.AuctionQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RedisDashboardServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);
    private RedisAuctionRealtimeStateReader stateReader;
    private RedisDashboardStateSeeder dashboardStateSeeder;
    private RedisDashboardService dashboardService;

    @BeforeEach
    void setUp() {
        stateReader = mock(RedisAuctionRealtimeStateReader.class);
        dashboardStateSeeder = mock(RedisDashboardStateSeeder.class);
        dashboardService = new RedisDashboardService(stateReader, dashboardStateSeeder, mock(AuctionQueryService.class), CLOCK);
    }

    @Test
    void Redis_승인_상태와_내_입찰_상태로_참여중인_경매를_반환한다() {
        given(stateReader.participatingAuctionIds(7)).willReturn(List.of(1, 2, 3));
        given(stateReader.readAuctionState(1)).willReturn(state(1, 120_000L, AuctionStatus.OPEN, CLOCK.instant().plusSeconds(60)));
        given(stateReader.readAuctionState(2)).willReturn(state(2, 300_000L, AuctionStatus.ENDING, CLOCK.instant().plusSeconds(120)));
        given(stateReader.readAuctionState(3)).willReturn(state(3, 500_000L, AuctionStatus.OPEN, CLOCK.instant().minusSeconds(1)));
        given(stateReader.read(1, 7)).willReturn(realtime(MyBidStatus.LEADING, 120_000L, AuctionStatus.OPEN, CLOCK.instant().plusSeconds(60)));
        given(stateReader.read(2, 7)).willReturn(realtime(MyBidStatus.OUTBID, 300_000L, AuctionStatus.ENDING, CLOCK.instant().plusSeconds(120)));
        given(stateReader.read(3, 7)).willReturn(realtime(MyBidStatus.LEADING, 500_000L, AuctionStatus.OPEN, CLOCK.instant().minusSeconds(1)));

        var result = dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.PRICE_HIGH);

        assertThat(result).extracting(snapshot -> snapshot.id()).containsExactly(2, 1);
        assertThat(result).extracting(snapshot -> snapshot.currentPrice()).containsExactly(300_000L, 120_000L);
        verify(dashboardStateSeeder).seedIfRequired(7);
    }

    private RedisAuctionRealtimeStateReader.AuctionState state(int id, long price, AuctionStatus status, Instant closeTime) {
        return new RedisAuctionRealtimeStateReader.AuctionState(
                id, status, 9, 100 + id, "카드", "세트", "10", "KR", "card.webp", "경매", "설명", null, null, null,
                false, 100_000L, price, 1_000L, 2, null, 3_000L, CLOCK.instant().minusSeconds(60), closeTime, List.of()
        );
    }

    private RedisAuctionRealtimeStateReader.RealtimeState realtime(MyBidStatus myBidStatus, long amount, AuctionStatus status, Instant closeTime) {
        return new RedisAuctionRealtimeStateReader.RealtimeState(status, amount, 1_000L, 2, closeTime, null, myBidStatus, amount, List.of());
    }
}
