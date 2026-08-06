package com.dbidding.auction.adapter;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.sse.auction.payload.AuctionClosedPayload;
import com.dbidding.sse.auction.payload.AuctionCreatedPayload;
import com.dbidding.sse.auction.payload.AuctionPayloadStatus;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class SpringAuctionEventPublisher implements AuctionEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishOpened(AuctionOpenedEvent event) {
        applicationEventPublisher.publishEvent(event);
        applicationEventPublisher.publishEvent(new AuctionCreatedPayload(
                null,
                event.auctionId(),
                event.itemId(),
                event.cardName(),
                event.cardPsaGrade(),
                event.cardLanguage(),
                event.cardThumbnailUrl(),
                event.sellerId(),
                event.startPrice(),
                event.currentPrice(),
                event.bidIncrement(),
                event.bidCount(),
                event.closeTime().toInstant(ZoneOffset.UTC),
                AuctionPayloadStatus.valueOf(event.status().name()),
                event.version(),
                event.occurredAt().toInstant(ZoneOffset.UTC)
        ));
    }

    @Override
    public void publishBidPlaced(BidPlacedEvent event) {
        applicationEventPublisher.publishEvent(event);
        applicationEventPublisher.publishEvent(new BidPlacedPayload(
                null,
                event.auctionId(),
                event.bidderId(),
                event.previousBidderId(),
                event.startPrice(),
                event.currentPrice(),
                event.bidIncrement(),
                event.bidCount(),
                event.closeTime().toInstant(ZoneOffset.UTC),
                AuctionPayloadStatus.valueOf(event.status().name()),
                event.version(),
                event.occurredAt().toInstant(ZoneOffset.UTC)
        ));
    }

    @Override
    public void publishClosed(AuctionClosedEvent event) {
        applicationEventPublisher.publishEvent(event);
        applicationEventPublisher.publishEvent(new AuctionClosedPayload(
                null,
                event.auctionId(),
                event.itemId(),
                event.cardName(),
                event.cardPsaGrade(),
                event.cardLanguage(),
                event.cardThumbnailUrl(),
                event.winnerId(),
                event.sellerId(),
                event.startPrice(),
                event.currentPrice(),
                event.bidIncrement(),
                event.bidCount(),
                event.closeTime().toInstant(ZoneOffset.UTC),
                AuctionPayloadStatus.valueOf(event.status().name()),
                event.version(),
                event.closeTime().toInstant(ZoneOffset.UTC),
                event.occurredAt().toInstant(ZoneOffset.UTC)
        ));
    }
}
