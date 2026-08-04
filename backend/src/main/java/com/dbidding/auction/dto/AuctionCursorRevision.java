package com.dbidding.auction.dto;

public record AuctionCursorRevision(
        Long auctionCount,
        Long versionSum
) {
}
