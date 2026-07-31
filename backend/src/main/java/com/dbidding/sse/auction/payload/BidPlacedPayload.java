package com.dbidding.sse.auction.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record BidPlacedPayload(
        AuctionPayloadType type,
        Integer auctionId,
        Integer bidderId,
        Integer previousBidderId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        Instant endsAt,
        AuctionPayloadStatus status,
        Long auctionVersion,
        Instant occurredAt
) implements AuctionPayload {

    public BidPlacedPayload {
        type = AuctionPayloadType.BID_PLACED;
    }
}
