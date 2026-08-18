package com.dbidding.auction.bid.redis;

import com.dbidding.auction.bid.dto.AuctionSeedData;
import com.dbidding.auction.bid.dto.AuctionSeedDbData;
import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** batch thread에서 경매 cold-seed용 MySQL projection을 한 트랜잭션으로 읽는다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionSeedDataLoader {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionImageRepository auctionImageRepository;

    @Transactional(readOnly = true)
    public Map<Integer, AuctionSeedDbData> load(List<Integer> auctionIds) {
        List<Auction> auctions = auctionRepository
                .findByIdInAndStatusNot(auctionIds, AuctionStatus.ENDED).stream()
                .filter(auction -> EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING)
                        .contains(auction.getStatus()))
                .toList();
        if (auctions.isEmpty()) return Map.of();

        List<Integer> activeIds = auctions.stream().map(Auction::getId).toList();
        Map<Integer, Bid> leadingByAuction = bidRepository
                .findByAuctionIdInAndStatus(activeIds, BidStatus.LEADING).stream()
                .collect(Collectors.toMap(
                        bid -> bid.getAuction().getId(),
                        Function.identity(),
                        (first, ignored) -> first));
        Map<Integer, List<Bid>> latestByAuction = bidRepository
                .findLatestBidPerBidderByAuctionIdIn(activeIds).stream()
                .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
        Map<Integer, List<Bid>> recentByAuction = bidRepository
                .findRecentFiveByAuctionIdIn(activeIds).stream()
                .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
        Map<Integer, List<String>> imagesByAuction = auctionImageRepository
                .findByAuctionIdInOrderById(activeIds).stream()
                .collect(Collectors.groupingBy(
                        image -> image.getAuction().getId(),
                        Collectors.mapping(AuctionImage::getImagePath, Collectors.toList())));
        return auctions.stream().collect(Collectors.toMap(
                Auction::getId,
                auction -> new AuctionSeedDbData(
                        auction,
                        leadingByAuction.get(auction.getId()),
                        imagesByAuction.getOrDefault(auction.getId(), List.of()),
                        latestByAuction.getOrDefault(auction.getId(), List.of()),
                        recentByAuction.getOrDefault(auction.getId(), List.of()))));
    }
}
