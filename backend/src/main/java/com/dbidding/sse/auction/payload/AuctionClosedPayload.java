package com.dbidding.sse.auction.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuctionClosedPayload(
        AuctionPayloadType type,
        Integer auctionId,
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer winnerId,
        Integer sellerId,
        Long startPrice,
        Long finalPrice,
        Long bidIncrement,
        Integer bidCount,
        Instant endsAt,
        AuctionPayloadStatus status,
        Long auctionVersion,
        Instant closedAt,
        Instant occurredAt
) implements AuctionPayload {

    public AuctionClosedPayload {
        type = AuctionPayloadType.AUCTION_CLOSED;
    }
}
