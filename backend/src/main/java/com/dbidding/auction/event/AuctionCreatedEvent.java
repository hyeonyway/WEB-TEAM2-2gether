package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record AuctionCreatedEvent(
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
        AuctionStatus status,
        Long auctionVersion,
        LocalDateTime occurredAt
) implements AuctionEvent {
}
