package com.dbidding.auction.bid.dto;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import java.util.List;

/** 경매 cold-seed에 필요한 MySQL 조회 결과. Redis/card cache 데이터는 포함하지 않는다. */
public record AuctionSeedDbData(
        Auction auction,
        Bid leading,
        List<String> imagePaths,
        List<Bid> latestBids,
        List<Bid> recentBids
) {
}
