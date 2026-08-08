package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record AuctionCloseResponse(
        @JsonProperty("auction_id") Integer auctionId,
        AuctionStatus status,
        @JsonProperty("winner_id") Integer winnerId,
        @JsonProperty("winning_bid_id") Long winningBidId,
        @JsonProperty("winning_price") Long winningPrice,
        @JsonProperty("closed_at") Instant closedAt
) {
}
