package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.Instant;

public record AuctionOpenedEvent(
        Integer auctionId,
        Integer itemId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId,
        Long startPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        Instant closeTime,
        AuctionStatus status,
        Instant occurredAt
) {
}
