package com.dbidding.auction.bid.dto;

public record BidCommand(Integer bidderId, Integer auctionId, Long price, String idempotencyKey) {
}
