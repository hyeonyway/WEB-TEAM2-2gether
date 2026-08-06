package com.dbidding.auction.adapter;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.sse.AuctionStreamPayload;
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
        applicationEventPublisher.publishEvent(AuctionStreamPayload.created(event));
    }

    @Override
    public void publishBidPlaced(BidPlacedEvent event) {
        applicationEventPublisher.publishEvent(event);
        applicationEventPublisher.publishEvent(AuctionStreamPayload.bidPlaced(event));
    }

    @Override
    public void publishClosed(AuctionClosedEvent event) {
        applicationEventPublisher.publishEvent(event);
        applicationEventPublisher.publishEvent(AuctionStreamPayload.closed(event));
    }
}
