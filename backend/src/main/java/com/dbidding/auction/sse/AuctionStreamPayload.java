package com.dbidding.auction.sse;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.time.ZoneOffset;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuctionStreamPayload(
        @JsonIgnore AuctionStreamEventType type,
        Integer auctionId,
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId,
        Integer bidderId,
        Integer previousBidderId,
        Integer winnerId,
        Long startPrice,
        Long currentPrice,
        Long finalPrice,
        Long bidIncrement,
        Integer bidCount,
        Instant endsAt,
        AuctionStatus status,
        Long auctionVersion,
        Instant closedAt,
        Instant occurredAt
) {
    public static AuctionStreamPayload created(AuctionOpenedEvent event) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.AUCTION_CREATED, event.auctionId(), event.itemId(), event.cardName(),
                event.cardPsaGrade(), event.cardLanguage(), event.cardThumbnailUrl(), event.sellerId(),
                null, null, null, event.startPrice(), event.currentPrice(), null,
                event.bidIncrement(), event.bidCount(), event.closeTime().toInstant(ZoneOffset.UTC),
                event.status(), event.version(), null, event.occurredAt().toInstant(ZoneOffset.UTC));
    }

    public static AuctionStreamPayload bidPlaced(BidPlacedEvent event) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.BID_PLACED, event.auctionId(), null, null, null, null, null, null,
                event.bidderId(), event.previousBidderId(), null, event.startPrice(), event.currentPrice(), null,
                event.bidIncrement(), event.bidCount(), event.closeTime().toInstant(ZoneOffset.UTC),
                event.status(), event.version(), null, event.occurredAt().toInstant(ZoneOffset.UTC));
    }

    public static AuctionStreamPayload closed(AuctionClosedEvent event) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.AUCTION_CLOSED, event.auctionId(), event.itemId(), event.cardName(),
                event.cardPsaGrade(), event.cardLanguage(), event.cardThumbnailUrl(), event.sellerId(),
                null, null, event.winnerId(), event.startPrice(), event.currentPrice(), event.winningPrice(),
                event.bidIncrement(), event.bidCount(), event.closeTime().toInstant(ZoneOffset.UTC),
                event.status(), event.version(), event.closeTime().toInstant(ZoneOffset.UTC),
                event.occurredAt().toInstant(ZoneOffset.UTC));
    }
}
