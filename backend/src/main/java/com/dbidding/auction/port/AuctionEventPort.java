package com.dbidding.auction.port;

import java.time.LocalDateTime;

public interface AuctionEventPort {
    void record(AuctionEvent event);

    record AuctionEvent(
            AuctionEventType type,
            Integer auctionId,
            Integer actorId,
            Long amount,
            LocalDateTime occurredAt
    ) {
    }

    enum AuctionEventType {
        AUCTION_OPENED,
        BID_PLACED,
        BID_OUTBID,
        AUCTION_ENDED
    }
}
