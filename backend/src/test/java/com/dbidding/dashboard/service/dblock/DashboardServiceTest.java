package com.dbidding.dashboard.service.dblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.dashboard.domain.ParticipatingAuctionSort;
import com.dbidding.dashboard.domain.RecentWinSort;
import com.dbidding.dashboard.dto.DashboardResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-31T03:00:00Z"), ZoneOffset.UTC);
    private AuctionQueryService auctionQueryService;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        auctionQueryService = mock(AuctionQueryService.class);
        dashboardService = new DashboardService(auctionQueryService, CLOCK);
    }

    @Test
    void 참여중인_경매를_현재가_높은순으로_정렬하고_종료시각이_지난_경매는_제외한다() {
        given(auctionQueryService.getDashboardAuctions(7)).willReturn(List.of(
                auction(1, AuctionStatus.OPEN, BidStatus.LEADING, 120_000L, CLOCK.instant().plus(Duration.ofDays(1))),
                auction(2, AuctionStatus.OPEN, BidStatus.OUTBID, 300_000L, CLOCK.instant().plus(Duration.ofDays(2))),
                auction(3, AuctionStatus.OPEN, BidStatus.LEADING, 500_000L, CLOCK.instant().minus(Duration.ofMinutes(1)))
        ));

        List<DashboardResponse.AuctionSnapshot> result =
                dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.PRICE_HIGH);

        assertThat(result).extracting(DashboardResponse.AuctionSnapshot::id).containsExactly(2, 1);
    }

    @Test
    void 최근_낙찰을_낙찰가_높은순으로_정렬한다() {
        given(auctionQueryService.getDashboardAuctions(7)).willReturn(List.of(
                auction(1, AuctionStatus.ENDED, BidStatus.WON, 100_000L, CLOCK.instant()),
                auction(2, AuctionStatus.ENDED, BidStatus.WON, 300_000L, CLOCK.instant())
        ));

        List<DashboardResponse.AuctionSnapshot> result =
                dashboardService.getRecentWins(7, RecentWinSort.PRICE_HIGH);

        assertThat(result).extracting(DashboardResponse.AuctionSnapshot::id).containsExactly(2, 1);
    }

    private AuctionResponses.DashboardAuction auction(
            int id, AuctionStatus status, BidStatus bidStatus, long bidAmount, Instant estimatedCloseTime
    ) {
        return new AuctionResponses.DashboardAuction(
                id, 9, new AuctionResponses.CardSummary(id, "카드 " + id, "세트", "10", "JP", "card.webp"),
                100_000L, bidAmount, 1_000L, 3, estimatedCloseTime, estimatedCloseTime,
                status, bidStatus, bidAmount
        );
    }
}
