package com.dbidding.auction.event;

public interface AuctionEventPublisher {
    void publishOpened(AuctionOpenedEvent event);
    void publishBidPlaced(BidPlacedEvent event);
    void publishClosed(AuctionClosedEvent event);
}
