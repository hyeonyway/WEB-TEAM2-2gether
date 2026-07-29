package com.dbidding.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.dashboard.dto.DashboardResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {
    private BidRepository bidRepository;
    private AuctionImageRepository auctionImageRepository;
    private AuctionCardPort auctionCardPort;
    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        bidRepository = mock(BidRepository.class);
        auctionImageRepository = mock(AuctionImageRepository.class);
        auctionCardPort = mock(AuctionCardPort.class);
        dashboardService = new DashboardService(
                bidRepository,
                auctionImageRepository,
                auctionCardPort
        );
    }

    @Test
    void 경매별_최신_입찰만_참여_목록에_표시한다() {
        Auction openAuction = auction(1, 101, AuctionStatus.OPEN, LocalDateTime.now().plusDays(1));
        Bid latest = bid(openAuction, BidStatus.LEADING, 150_000L);
        Bid older = bid(openAuction, BidStatus.OUTBID, 130_000L);
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(7))
                .willReturn(List.of(latest, older));
        given(auctionCardPort.getCardSnapshots(List.of(101)))
                .willReturn(Map.of(101, card(101)));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(1)))
                .willReturn(List.of());

        List<DashboardResponse.AuctionSnapshot> result =
                dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.ENDING_SOON);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().myBidAmount()).isEqualTo(150_000L);
    }

    @Test
    void 종료된_낙찰은_최근_종료순으로_반환한다() {
        Auction olderAuction = auction(1, 101, AuctionStatus.ENDED, LocalDateTime.of(2026, 7, 29, 10, 0));
        Auction recentAuction = auction(2, 102, AuctionStatus.ENDED, LocalDateTime.of(2026, 7, 29, 12, 0));
        given(olderAuction.getCloseTime()).willReturn(LocalDateTime.of(2026, 7, 29, 10, 0));
        given(recentAuction.getCloseTime()).willReturn(LocalDateTime.of(2026, 7, 29, 12, 0));
        Bid olderWin = bid(olderAuction, BidStatus.WON, 100_000L);
        Bid recentWin = bid(recentAuction, BidStatus.WON, 200_000L);
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(7))
                .willReturn(List.of(olderWin, recentWin));
        given(auctionCardPort.getCardSnapshots(List.of(101, 102)))
                .willReturn(Map.of(101, card(101), 102, card(102)));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(1, 2)))
                .willReturn(List.of());

        List<DashboardResponse.AuctionSnapshot> participating =
                dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.ENDING_SOON);
        List<DashboardResponse.AuctionSnapshot> recentWins =
                dashboardService.getRecentWins(7, RecentWinSort.LATEST);

        assertThat(participating).isEmpty();
        assertThat(recentWins).extracting(DashboardResponse.AuctionSnapshot::id)
                .containsExactly(2, 1);
    }

    @Test
    void 입찰이_없으면_빈_목록을_반환한다() {
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(7)).willReturn(List.of());

        List<DashboardResponse.AuctionSnapshot> participating =
                dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.ENDING_SOON);
        List<DashboardResponse.AuctionSnapshot> recentWins =
                dashboardService.getRecentWins(7, RecentWinSort.LATEST);

        assertThat(participating).isEmpty();
        assertThat(recentWins).isEmpty();
    }

    @Test
    void 참여중인_경매를_현재가_높은순으로_정렬한다() {
        Auction cheaper = auction(1, 101, AuctionStatus.OPEN, LocalDateTime.now().plusDays(1));
        Auction expensive = auction(2, 102, AuctionStatus.OPEN, LocalDateTime.now().plusDays(2));
        given(cheaper.getCurrentPrice()).willReturn(120_000L);
        given(expensive.getCurrentPrice()).willReturn(300_000L);
        Bid cheaperBid = bid(cheaper, BidStatus.LEADING, 120_000L);
        Bid expensiveBid = bid(expensive, BidStatus.OUTBID, 250_000L);
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(7))
                .willReturn(List.of(cheaperBid, expensiveBid));
        given(auctionCardPort.getCardSnapshots(List.of(102, 101)))
                .willReturn(Map.of(101, card(101), 102, card(102)));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(2, 1)))
                .willReturn(List.of());

        List<DashboardResponse.AuctionSnapshot> result =
                dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.PRICE_HIGH);

        assertThat(result).extracting(DashboardResponse.AuctionSnapshot::id)
                .containsExactly(2, 1);
    }

    @Test
    void 상태가_진행중이어도_종료시각이_지난_경매는_참여_목록에서_제외한다() {
        Auction expired = auction(
                1,
                101,
                AuctionStatus.OPEN,
                LocalDateTime.now().minusMinutes(1)
        );
        Bid expiredBid = bid(expired, BidStatus.LEADING, 150_000L);
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(7))
                .willReturn(List.of(expiredBid));

        List<DashboardResponse.AuctionSnapshot> result =
                dashboardService.getParticipatingAuctions(7, ParticipatingAuctionSort.ENDING_SOON);

        assertThat(result).isEmpty();
    }

    @Test
    void 최근_낙찰을_오래된순과_낙찰가_높은순으로_정렬한다() {
        Auction olderAuction = auction(1, 101, AuctionStatus.ENDED, LocalDateTime.of(2026, 7, 28, 12, 0));
        Auction recentAuction = auction(2, 102, AuctionStatus.ENDED, LocalDateTime.of(2026, 7, 29, 12, 0));
        given(olderAuction.getCloseTime()).willReturn(LocalDateTime.of(2026, 7, 28, 12, 0));
        given(recentAuction.getCloseTime()).willReturn(LocalDateTime.of(2026, 7, 29, 12, 0));
        Bid cheaperRecentWin = bid(recentAuction, BidStatus.WON, 100_000L);
        Bid expensiveOlderWin = bid(olderAuction, BidStatus.WON, 300_000L);
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(7))
                .willReturn(List.of(cheaperRecentWin, expensiveOlderWin));
        given(auctionCardPort.getCardSnapshots(List.of(101, 102)))
                .willReturn(Map.of(101, card(101), 102, card(102)));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(1, 2)))
                .willReturn(List.of());

        List<DashboardResponse.AuctionSnapshot> oldest =
                dashboardService.getRecentWins(7, RecentWinSort.OLDEST);
        List<DashboardResponse.AuctionSnapshot> priceHigh =
                dashboardService.getRecentWins(7, RecentWinSort.PRICE_HIGH);

        assertThat(oldest).extracting(DashboardResponse.AuctionSnapshot::id)
                .containsExactly(1, 2);
        assertThat(priceHigh).extracting(DashboardResponse.AuctionSnapshot::id)
                .containsExactly(1, 2);
    }

    private Auction auction(Integer id, Integer itemId, AuctionStatus status, LocalDateTime endsAt) {
        Auction auction = mock(Auction.class);
        given(auction.getId()).willReturn(id);
        given(auction.getItemId()).willReturn(itemId);
        given(auction.getAuctionName()).willReturn("경매 " + id);
        given(auction.getStatus()).willReturn(status);
        given(auction.getStartPrice()).willReturn(100_000L);
        given(auction.getCurrentPrice()).willReturn(150_000L);
        given(auction.getBidPriceUnit()).willReturn(1_000L);
        given(auction.getBidCount()).willReturn(3);
        given(auction.getEstimatedCloseTime()).willReturn(endsAt);
        given(auction.getVersion()).willReturn(1L);
        return auction;
    }

    private Bid bid(Auction auction, BidStatus status, Long amount) {
        Bid bid = mock(Bid.class);
        given(bid.getAuction()).willReturn(auction);
        given(bid.getStatus()).willReturn(status);
        given(bid.getBidPrice()).willReturn(amount);
        return bid;
    }

    private AuctionCardPort.CardSnapshot card(Integer itemId) {
        return new AuctionCardPort.CardSnapshot(
                itemId,
                "카드 " + itemId,
                "세트",
                "10",
                "JP",
                "card.webp"
        );
    }
}
