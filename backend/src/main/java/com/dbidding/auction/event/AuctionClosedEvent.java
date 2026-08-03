package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record AuctionClosedEvent(
        Integer auctionId,
        Integer itemId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer winnerId,
        Integer sellerId,
        Long startPrice,
        Long currentPrice,
        Long winningPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime closeTime,
        AuctionStatus status,
        Long version,
        LocalDateTime occurredAt
) {
}
