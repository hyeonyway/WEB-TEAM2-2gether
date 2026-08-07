package com.dbidding.dashboard;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.dashboard.dto.DashboardResponse;
import com.dbidding.global.time.UtcTime;
import java.time.LocalDateTime;
import java.time.Clock;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {
    private static final Set<AuctionStatus> PARTICIPATING_STATUSES =
            Set.of(AuctionStatus.OPEN, AuctionStatus.ENDING);

    private final BidRepository bidRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final CardService cardService;
    private final Clock clock;

    public List<DashboardResponse.AuctionSnapshot> getParticipatingAuctions(
            Integer userId,
            ParticipatingAuctionSort sort
    ) {
        Map<Integer, Bid> latestBids = latestBidsByAuction(userId);
        List<Bid> participating = latestBids.values().stream()
                .filter(bid -> PARTICIPATING_STATUSES.contains(bid.getAuction().getStatus()))
                .filter(bid -> bid.getAuction().getEstimatedCloseTime()
                        .isAfter(LocalDateTime.now(clock)))
                .sorted(participatingComparator(sort))
                .toList();
        return snapshots(participating);
    }

    public List<DashboardResponse.AuctionSnapshot> getRecentWins(
            Integer userId,
            RecentWinSort sort
    ) {
        List<Bid> recentWins = latestBidsByAuction(userId).values().stream()
                .filter(bid -> bid.getStatus() == BidStatus.WON)
                .sorted(recentWinComparator(sort))
                .toList();
        return snapshots(recentWins);
    }

    private List<DashboardResponse.AuctionSnapshot> snapshots(List<Bid> bids) {
        List<Auction> auctions = bids.stream()
                .map(Bid::getAuction)
                .distinct()
                .toList();
        Map<Integer, CardSnapshot> cards = cardSnapshots(auctions);
        Map<Integer, String> images = firstImages(auctions);
        return bids.stream().map(bid -> snapshot(bid, cards, images)).toList();
    }

    private Map<Integer, Bid> latestBidsByAuction(Integer userId) {
        Map<Integer, Bid> latest = new LinkedHashMap<>();
        bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(userId)
                .forEach(bid -> latest.putIfAbsent(bid.getAuction().getId(), bid));
        return latest;
    }

    private Comparator<Bid> participatingComparator(ParticipatingAuctionSort sort) {
        return switch (sort) {
            case ENDING_SOON -> Comparator
                    .comparing((Bid bid) -> bid.getAuction().getEstimatedCloseTime())
                    .thenComparing(bid -> bid.getAuction().getId());
            case PRICE_HIGH -> Comparator
                    .comparing(
                            (Bid bid) -> bid.getAuction().getCurrentPrice(),
                            Comparator.reverseOrder()
                    )
                    .thenComparing(bid -> bid.getAuction().getId());
        };
    }

    private Comparator<Bid> recentWinComparator(RecentWinSort sort) {
        Comparator<Bid> comparator = switch (sort) {
            case LATEST -> Comparator.comparing(
                    (Bid bid) -> bid.getAuction().getCloseTime(),
                    Comparator.reverseOrder()
            );
            case OLDEST -> Comparator.comparing(bid -> bid.getAuction().getCloseTime());
            case PRICE_HIGH -> Comparator.comparing(
                    Bid::getBidPrice,
                    Comparator.reverseOrder()
            );
        };
        return comparator.thenComparing(bid -> bid.getAuction().getId());
    }

    private Map<Integer, CardSnapshot> cardSnapshots(List<Auction> auctions) {
        List<Integer> itemIds = auctions.stream().map(Auction::getItemId).distinct().toList();
        return itemIds.isEmpty() ? Map.of() : cardService.getCardSnapshots(itemIds);
    }

    private Map<Integer, String> firstImages(List<Auction> auctions) {
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).distinct().toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, String> images = new LinkedHashMap<>();
        auctionImageRepository.findByAuctionIdInOrderById(auctionIds)
                .forEach(image -> images.putIfAbsent(
                        image.getAuction().getId(),
                        image.getImagePath()
                ));
        return images;
    }

    private DashboardResponse.AuctionSnapshot snapshot(
            Bid bid,
            Map<Integer, CardSnapshot> cards,
            Map<Integer, String> images
    ) {
        Auction auction = bid.getAuction();
        CardSnapshot card = cards.get(auction.getItemId());
        String thumbnail = images.getOrDefault(
                auction.getId(),
                card == null ? null : card.thumbnailUrl()
        );
        return new DashboardResponse.AuctionSnapshot(
                auction.getId(),
                auction.getSellerId(),
                new DashboardResponse.CardSnapshot(
                        auction.getItemId(),
                        card == null ? auction.getAuctionName() : card.name(),
                        card == null ? null : card.psaGrade(),
                        card == null ? null : card.language(),
                        thumbnail
                ),
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.getBidPriceUnit(),
                auction.getBidCount(),
                UtcTime.toInstant(auction.getEstimatedCloseTime()),
                auction.getStatus(),
                auction.getVersion(),
                myBidStatus(bid),
                bid.getBidPrice()
        );
    }

    private MyBidStatus myBidStatus(Bid bid) {
        return switch (bid.getStatus()) {
            case LEADING, WON -> MyBidStatus.LEADING;
            case OUTBID, CANCELLED -> MyBidStatus.OUTBID;
        };
    }
}
