package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.Instant;

public record BidPlacedEvent(
        Integer auctionId,
        Integer itemId,
        Integer bidderId,
        Integer previousBidderId,
        Long previousBidId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        Instant closeTime,
        AuctionStatus status,
        Instant occurredAt
) {
}
