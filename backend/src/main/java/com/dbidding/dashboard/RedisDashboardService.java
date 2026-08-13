package com.dbidding.dashboard;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.dashboard.dto.DashboardResponse;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
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
    private final AuctionQueryService auctionQueryService;
    private final Clock clock;

    @Override
    public List<DashboardResponse.AuctionSnapshot> getParticipatingAuctions(Integer userId, ParticipatingAuctionSort sort) {
        return realtimeStateReader.participatingAuctionIds(userId).stream()
                .map(auctionId -> snapshot(auctionId, userId))
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

    private DashboardResponse.AuctionSnapshot snapshot(Integer auctionId, Integer userId) {
        RedisAuctionRealtimeStateReader.AuctionState state = realtimeStateReader.readAuctionState(auctionId);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(auctionId, userId);
        if (state == null || realtime == null) return null;
        return new DashboardResponse.AuctionSnapshot(
                state.auctionId(), state.sellerId(),
                new DashboardResponse.CardSnapshot(state.itemId(), state.cardName(), state.cardPsaGrade(), state.cardLanguage(), state.cardThumbnailUrl()),
                state.startPrice(), state.currentPrice(), state.bidIncrement(), state.bidCount(), state.closeTime(), state.status(),
                realtime.myBidStatus(), realtime.myBidAmount()
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
