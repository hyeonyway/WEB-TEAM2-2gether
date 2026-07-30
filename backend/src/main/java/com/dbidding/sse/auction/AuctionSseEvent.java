package com.dbidding.sse.auction;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionCreatedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.LocalDateTime;

public sealed interface AuctionSseEvent permits
        AuctionSseEvent.AuctionCreated,
        AuctionSseEvent.BidPlaced,
        AuctionSseEvent.AuctionClosed {

    EventType type();

    Integer auctionId();

    Long auctionVersion();

    enum EventType {
        AUCTION_CREATED,
        BID_PLACED,
        AUCTION_CLOSED
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AuctionCreated(
            EventType type,
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
    ) implements AuctionSseEvent {
        static AuctionCreated from(AuctionCreatedEvent event) {
            return new AuctionCreated(
                    EventType.AUCTION_CREATED,
                    event.auctionId(),
                    event.cardId(),
                    event.cardName(),
                    event.cardPsaGrade(),
                    event.cardLanguage(),
                    event.cardThumbnailUrl(),
                    event.sellerId(),
                    event.startPrice(),
                    event.currentPrice(),
                    event.bidIncrement(),
                    event.bidCount(),
                    event.endsAt(),
                    event.status(),
                    event.auctionVersion(),
                    event.occurredAt()
            );
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record BidPlaced(
            EventType type,
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
    ) implements AuctionSseEvent {
        static BidPlaced from(BidPlacedEvent event) {
            return new BidPlaced(
                    EventType.BID_PLACED,
                    event.auctionId(),
                    event.cardId(),
                    event.cardName(),
                    event.cardPsaGrade(),
                    event.cardLanguage(),
                    event.cardThumbnailUrl(),
                    event.sellerId(),
                    event.bidderId(),
                    event.previousBidderId(),
                    event.startPrice(),
                    event.bidPrice(),
                    event.currentPrice(),
                    event.bidIncrement(),
                    event.bidCount(),
                    event.endsAt(),
                    event.status(),
                    event.auctionVersion(),
                    event.occurredAt()
            );
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record AuctionClosed(
            EventType type,
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
    ) implements AuctionSseEvent {
        static AuctionClosed from(AuctionClosedEvent event) {
            return new AuctionClosed(
                    EventType.AUCTION_CLOSED,
                    event.auctionId(),
                    event.cardId(),
                    event.cardName(),
                    event.cardPsaGrade(),
                    event.cardLanguage(),
                    event.cardThumbnailUrl(),
                    event.winnerId(),
                    event.sellerId(),
                    event.startPrice(),
                    event.finalPrice(),
                    event.bidIncrement(),
                    event.bidCount(),
                    event.endsAt(),
                    event.status(),
                    event.auctionVersion(),
                    event.closedAt(),
                    event.occurredAt()
            );
        }
    }
}
