package com.dbidding.auction.bid;

public record BidCommand(Integer bidderId, Integer auctionId, Long price, String idempotencyKey) {
}
