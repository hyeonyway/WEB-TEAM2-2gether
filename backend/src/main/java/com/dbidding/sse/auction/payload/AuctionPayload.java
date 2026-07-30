package com.dbidding.sse.auction.payload;

public sealed interface AuctionPayload permits
        AuctionCreatedPayload,
        BidPlacedPayload,
        AuctionClosedPayload {

    AuctionPayloadType type();

    Integer auctionId();

    Long auctionVersion();
}
