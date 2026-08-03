package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record BidPlacedEvent(
        Integer auctionId,
        Integer itemId,
        Integer bidderId,
        Integer previousBidderId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime closeTime,
        AuctionStatus status,
        Long version,
        LocalDateTime occurredAt
) {
}
