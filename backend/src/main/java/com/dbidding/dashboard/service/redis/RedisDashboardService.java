package com.dbidding.dashboard.service.redis;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.dashboard.domain.ParticipatingAuctionSort;
import com.dbidding.dashboard.domain.RecentWinSort;
import com.dbidding.dashboard.dto.DashboardResponse;
import com.dbidding.dashboard.service.DashboardQueryService;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Redis 승인 상태를 기준으로 진행 중인 내 입찰 경매를 반환한다. */
@Service
@Profile("redis")
@RequiredArgsConstructor
public class RedisDashboardService implements DashboardQueryService {
    private static final Set<AuctionStatus> PARTICIPATING_STATUSES = Set.of(AuctionStatus.OPEN, AuctionStatus.ENDING);

    private final RedisAuctionRealtimeStateReader realtimeStateReader;
    private final RedisDashboardStateSeeder dashboardStateSeeder;
    private final AuctionQueryService auctionQueryService;
    private final Clock clock;

    @Override
    public List<DashboardResponse.AuctionSnapshot> getParticipatingAuctions(Integer userId, ParticipatingAuctionSort sort) {
        dashboardStateSeeder.seedIfRequired(userId);
        List<Integer> auctionIds = realtimeStateReader.participatingAuctionIds(userId);
        Map<Integer, RedisAuctionRealtimeStateReader.AuctionState> states = realtimeStateReader.readAuctionStates(auctionIds);
        Map<Integer, RedisAuctionRealtimeStateReader.MyBidState> myBids = realtimeStateReader.readMyBidStates(auctionIds, userId);
        return auctionIds.stream()
                .map(auctionId -> snapshot(states.get(auctionId), myBids.get(auctionId)))
                .filter(snapshot -> snapshot != null && snapshot.myBidStatus() != MyBidStatus.NONE)
                .filter(snapshot -> PARTICIPATING_STATUSES.contains(snapshot.status()))
                .filter(snapshot -> snapshot.endsAt().isAfter(clock.instant()))
                .sorted(participatingComparator(sort))
                .toList();
    }

    /** 종료 경매의 낙찰 이력은 Redis active state에서 제거되므로 MySQL projection을 유지한다. */
    @Override
    public List<DashboardResponse.AuctionSnapshot> getRecentWins(Integer userId, RecentWinSort sort) {
        return auctionQueryService.getDashboardAuctions(userId).stream()
                .filter(auction -> auction.bidStatus() == BidStatus.WON)
                .sorted(recentWinComparator(sort))
                .map(this::snapshot)
                .toList();
    }

    private DashboardResponse.AuctionSnapshot snapshot(
            RedisAuctionRealtimeStateReader.AuctionState state,
            RedisAuctionRealtimeStateReader.MyBidState myBid
    ) {
        if (state == null) return null;
        return new DashboardResponse.AuctionSnapshot(
                state.auctionId(), state.sellerId(),
                new DashboardResponse.CardSnapshot(state.itemId(), state.cardName(), state.cardPsaGrade(), state.cardLanguage(), state.cardThumbnailUrl()),
                state.startPrice(), state.currentPrice(), state.bidIncrement(), state.bidCount(), state.closeTime(), state.status(),
                myBid == null ? MyBidStatus.NONE : myBid.status(), myBid == null ? null : myBid.amount()
        );
    }

    private Comparator<DashboardResponse.AuctionSnapshot> participatingComparator(ParticipatingAuctionSort sort) {
        return switch (sort) {
            case ENDING_SOON -> Comparator.comparing(DashboardResponse.AuctionSnapshot::endsAt)
                    .thenComparing(DashboardResponse.AuctionSnapshot::id);
            case PRICE_HIGH -> Comparator.comparing(DashboardResponse.AuctionSnapshot::currentPrice, Comparator.reverseOrder())
                    .thenComparing(DashboardResponse.AuctionSnapshot::id);
        };
    }

    private Comparator<AuctionResponses.DashboardAuction> recentWinComparator(RecentWinSort sort) {
        Comparator<AuctionResponses.DashboardAuction> comparator = switch (sort) {
            case LATEST -> Comparator.comparing(AuctionResponses.DashboardAuction::closeTime, Comparator.reverseOrder());
            case OLDEST -> Comparator.comparing(AuctionResponses.DashboardAuction::closeTime);
            case PRICE_HIGH -> Comparator.comparing(AuctionResponses.DashboardAuction::bidAmount, Comparator.reverseOrder());
        };
        return comparator.thenComparing(AuctionResponses.DashboardAuction::id);
    }

    private DashboardResponse.AuctionSnapshot snapshot(AuctionResponses.DashboardAuction auction) {
        var card = auction.card();
        MyBidStatus myBidStatus = switch (auction.bidStatus()) {
            case LEADING, WON -> MyBidStatus.LEADING;
            case OUTBID, CANCELLED -> MyBidStatus.OUTBID;
        };
        return new DashboardResponse.AuctionSnapshot(
                auction.id(), auction.sellerId(),
                new DashboardResponse.CardSnapshot(card.id(), card.name(), card.psaGrade(), card.language(), card.thumbnailUrl()),
                auction.startPrice(), auction.currentPrice(), auction.bidIncrement(), auction.bidCount(), auction.estimatedCloseTime(), auction.status(),
                myBidStatus, auction.bidAmount()
        );
    }
}
