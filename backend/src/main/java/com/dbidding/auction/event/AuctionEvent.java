package com.dbidding.auction.event;

public sealed interface AuctionEvent permits AuctionCreatedEvent, BidPlacedEvent, AuctionClosedEvent {
    Integer auctionId();

    Long auctionVersion();
}
