package com.dbidding.dashboard.service.dblock;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.service.AuctionQueryService;
import com.dbidding.dashboard.domain.ParticipatingAuctionSort;
import com.dbidding.dashboard.domain.RecentWinSort;
import com.dbidding.dashboard.dto.DashboardResponse;
import com.dbidding.dashboard.service.DashboardQueryService;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@Profile("!redis")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService implements DashboardQueryService {
    private static final Set<AuctionStatus> PARTICIPATING_STATUSES =
            Set.of(AuctionStatus.OPEN, AuctionStatus.ENDING);

    private final AuctionQueryService auctionQueryService;
    private final Clock clock;

    public List<DashboardResponse.AuctionSnapshot> getParticipatingAuctions(
            Integer userId,
            ParticipatingAuctionSort sort
    ) {
        List<AuctionResponses.DashboardAuction> participating = auctionQueryService.getDashboardAuctions(userId).stream()
                .filter(auction -> PARTICIPATING_STATUSES.contains(auction.status()))
                .filter(auction -> auction.estimatedCloseTime()
                        .isAfter(clock.instant()))
                .sorted(participatingComparator(sort))
                .toList();
        return snapshots(participating);
    }

    public List<DashboardResponse.AuctionSnapshot> getRecentWins(
            Integer userId,
            RecentWinSort sort
    ) {
        List<AuctionResponses.DashboardAuction> recentWins = auctionQueryService.getDashboardAuctions(userId).stream()
                .filter(auction -> auction.bidStatus() == BidStatus.WON)
                .sorted(recentWinComparator(sort))
                .toList();
        return snapshots(recentWins);
    }

    private List<DashboardResponse.AuctionSnapshot> snapshots(List<AuctionResponses.DashboardAuction> auctions) {
        return auctions.stream().map(this::snapshot).toList();
    }

    private Comparator<AuctionResponses.DashboardAuction> participatingComparator(ParticipatingAuctionSort sort) {
        return switch (sort) {
            case ENDING_SOON -> Comparator
                    .comparing(AuctionResponses.DashboardAuction::estimatedCloseTime)
                    .thenComparing(AuctionResponses.DashboardAuction::id);
            case PRICE_HIGH -> Comparator
                    .comparing(
                            AuctionResponses.DashboardAuction::currentPrice,
                            Comparator.reverseOrder()
                    )
                    .thenComparing(AuctionResponses.DashboardAuction::id);
        };
    }

    private Comparator<AuctionResponses.DashboardAuction> recentWinComparator(RecentWinSort sort) {
        Comparator<AuctionResponses.DashboardAuction> comparator = switch (sort) {
            case LATEST -> Comparator.comparing(
                    AuctionResponses.DashboardAuction::closeTime,
                    Comparator.reverseOrder()
            );
            case OLDEST -> Comparator.comparing(AuctionResponses.DashboardAuction::closeTime);
            case PRICE_HIGH -> Comparator.comparing(
                    AuctionResponses.DashboardAuction::bidAmount,
                    Comparator.reverseOrder()
            );
        };
        return comparator.thenComparing(AuctionResponses.DashboardAuction::id);
    }

    private DashboardResponse.AuctionSnapshot snapshot(AuctionResponses.DashboardAuction auction) {
        var card = auction.card();
        return new DashboardResponse.AuctionSnapshot(
                auction.id(), auction.sellerId(),
                new DashboardResponse.CardSnapshot(
                        card.id(), card.name(), card.psaGrade(), card.language(), card.thumbnailUrl()
                ),
                auction.startPrice(), auction.currentPrice(), auction.bidIncrement(), auction.bidCount(),
                auction.estimatedCloseTime(), auction.status(),
                myBidStatus(auction.bidStatus()), auction.bidAmount()
        );
    }

    private MyBidStatus myBidStatus(BidStatus bidStatus) {
        return switch (bidStatus) {
            case LEADING, WON -> MyBidStatus.LEADING;
            case OUTBID, CANCELLED -> MyBidStatus.OUTBID;
        };
    }
}
