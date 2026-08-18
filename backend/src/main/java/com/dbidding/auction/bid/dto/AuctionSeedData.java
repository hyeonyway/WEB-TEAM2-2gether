package com.dbidding.auction.bid.dto;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import java.util.List;

/** 배치 콜드시드 조회 결과를 경매 1건 기준으로 묶은 것. */
public record AuctionSeedData(
        Auction auction, Bid leading, CardSnapshot card, List<String> imagePaths, List<Bid> latestBids, List<Bid> recentBids
) {
}
