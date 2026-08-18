package com.dbidding.auction.adapter;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SpringAuctionEventPublisher implements AuctionEventPublisher {
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void publishOpened(AuctionOpenedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishBidPlaced(BidPlacedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    @Override
    public void publishClosed(AuctionClosedEvent event) {
        applicationEventPublisher.publishEvent(event);
    }
}
