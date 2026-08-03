package com.dbidding.sse.auction.payload;

import com.fasterxml.jackson.annotation.JsonIgnore;

public sealed interface AuctionPayload permits
        AuctionCreatedPayload,
        BidPlacedPayload,
        AuctionClosedPayload {

    @JsonIgnore
    AuctionPayloadType type();

    Integer auctionId();

    Long auctionVersion();
}
