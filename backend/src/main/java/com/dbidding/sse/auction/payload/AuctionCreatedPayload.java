package com.dbidding.sse.auction.payload;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuctionCreatedPayload(
        AuctionPayloadType type,
        Integer auctionId,
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime endsAt,
        AuctionPayloadStatus status,
        Long auctionVersion,
        LocalDateTime occurredAt
) implements AuctionPayload {

    public AuctionCreatedPayload {
        type = AuctionPayloadType.AUCTION_CREATED;
    }
}
