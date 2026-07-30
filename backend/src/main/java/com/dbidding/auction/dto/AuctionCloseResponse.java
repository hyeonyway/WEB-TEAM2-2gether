package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record AuctionCloseResponse(
        Integer auctionId,
        AuctionStatus status,
        Integer winnerId,
        Long winningBidId,
        Long winningPrice,
        LocalDateTime closedAt,
        Long version
) {
}
