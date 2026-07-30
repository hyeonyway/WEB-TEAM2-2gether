package com.dbidding.auction.event;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.LocalDateTime;

public record BidPlacedEvent(
        Integer auctionId,
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId,
        Integer bidderId,
        Integer previousBidderId,
        Long startPrice,
        Long bidPrice,
        Long currentPrice,
        Long bidIncrement,
        Integer bidCount,
        LocalDateTime endsAt,
        AuctionStatus status,
        Long auctionVersion,
        LocalDateTime occurredAt
) implements AuctionEvent {
}
