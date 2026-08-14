package com.dbidding.auction.bid;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 배치 콜드시드 조회 결과를 경매 1건 기준으로 묶은 것. */
public record AuctionSeedData(
        Auction auction, Bid leading, CardSnapshot card, List<String> imagePaths, List<Bid> latestBids, List<Bid> recentBids
) {

    /** auctionId 목록에 대한 배치 조회를 한 번씩만 실행하고, 경매별로 묶어서 돌려준다. */
    static Map<Integer, AuctionSeedData> resolveBatch(
            List<Integer> auctionIds, AuctionRepository auctionRepository, BidRepository bidRepository,
            AuctionImageRepository auctionImageRepository, RedisCardStateReader cardStateReader
    ) {
        List<Auction> auctions = auctionRepository.findByIdInAndStatusNot(auctionIds, AuctionStatus.ENDED).stream()
                .filter(auction -> EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING).contains(auction.getStatus()))
                .toList();
        if (auctions.isEmpty()) return Map.of();
        List<Integer> activeIds = auctions.stream().map(Auction::getId).toList();
        Map<Integer, Bid> leadingByAuction = bidRepository.findByAuctionIdInAndStatus(activeIds, BidStatus.LEADING).stream()
                .collect(Collectors.toMap(bid -> bid.getAuction().getId(), bid -> bid, (first, ignored) -> first));
        Map<Integer, List<Bid>> latestBidsByAuction = bidRepository.findLatestBidPerBidderByAuctionIdIn(activeIds).stream()
                .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
        Map<Integer, List<Bid>> recentBidsByAuction = bidRepository.findRecentFiveByAuctionIdIn(activeIds).stream()
                .collect(Collectors.groupingBy(bid -> bid.getAuction().getId()));
        Map<Integer, CardSnapshot> cards = cardStateReader.getCardSnapshots(auctions.stream().map(Auction::getItemId).distinct().toList());
        Map<Integer, List<String>> imagePathsByAuction = auctionImageRepository.findByAuctionIdInOrderById(activeIds).stream()
                .collect(Collectors.groupingBy(image -> image.getAuction().getId(), Collectors.mapping(image -> image.getImagePath(), Collectors.toList())));
        return auctions.stream().collect(Collectors.toMap(Auction::getId, auction -> new AuctionSeedData(
                auction, leadingByAuction.get(auction.getId()), cards.get(auction.getItemId()),
                imagePathsByAuction.getOrDefault(auction.getId(), List.of()),
                latestBidsByAuction.getOrDefault(auction.getId(), List.of()),
                recentBidsByAuction.getOrDefault(auction.getId(), List.of())
        )));
    }
}
