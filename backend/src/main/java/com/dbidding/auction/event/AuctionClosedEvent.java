package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record AuctionClosedEvent(
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
        LocalDateTime endsAt,
        AuctionStatus status,
        Long auctionVersion,
        LocalDateTime closedAt,
        LocalDateTime occurredAt
) implements AuctionEvent {
}
