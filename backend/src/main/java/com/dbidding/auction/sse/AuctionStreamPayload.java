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
        Instant closedAt,
        Instant occurredAt,
        Instant publishedAt
) {
    public static AuctionStreamPayload created(AuctionOpenedEvent event) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.AUCTION_CREATED, event.auctionId(), event.itemId(), event.cardName(),
                event.cardPsaGrade(), event.cardLanguage(), event.cardThumbnailUrl(), event.sellerId(),
                null, null, null, event.startPrice(), event.currentPrice(), null,
                event.bidIncrement(), event.bidCount(), event.closeTime(),
                event.status(), null, event.occurredAt(), null);
    }

    public static AuctionStreamPayload bidPlaced(BidPlacedEvent event) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.BID_PLACED, event.auctionId(), null, null, null, null, null, null,
                event.bidderId(), event.previousBidderId(), null, event.startPrice(), event.currentPrice(), null,
                event.bidIncrement(), event.bidCount(), event.closeTime(),
                event.status(), null, event.occurredAt(), null);
    }

    public static AuctionStreamPayload closed(AuctionClosedEvent event) {
        return new AuctionStreamPayload(
                AuctionStreamEventType.AUCTION_CLOSED, event.auctionId(), event.itemId(), event.cardName(),
                event.cardPsaGrade(), event.cardLanguage(), event.cardThumbnailUrl(), event.sellerId(),
                null, null, event.winnerId(), event.startPrice(), event.currentPrice(), event.winningPrice(),
                event.bidIncrement(), event.bidCount(), event.closeTime(),
                event.status(), event.closeTime(),
                event.occurredAt(), null);
    }

    public AuctionStreamPayload withPublishedAt(Instant publishedAt) {
        return new AuctionStreamPayload(
                type, auctionId, cardId, cardName, cardPsaGrade, cardLanguage, cardThumbnailUrl, sellerId,
                bidderId, previousBidderId, winnerId, startPrice, currentPrice, finalPrice, bidIncrement,
                bidCount, endsAt, status, closedAt, occurredAt, publishedAt
        );
    }

    /**
     * {@code type}이 {@code @JsonIgnore}라 JSON 왕복(Redis pub/sub)에서 유실되므로, 수신 측이
     * {@link AuctionStreamMessage}로 따로 전달받은 타입을 다시 채워넣을 때 쓴다.
     */
    public AuctionStreamPayload withType(AuctionStreamEventType type) {
        return new AuctionStreamPayload(
                type, auctionId, cardId, cardName, cardPsaGrade, cardLanguage, cardThumbnailUrl, sellerId,
                bidderId, previousBidderId, winnerId, startPrice, currentPrice, finalPrice, bidIncrement,
                bidCount, endsAt, status, closedAt, occurredAt, publishedAt
        );
    }
}
