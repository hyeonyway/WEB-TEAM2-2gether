package com.dbidding.auction.port;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;

public interface AuctionEventPort {
    void publishOpened(AuctionOpenedEvent event);

    void publishBidPlaced(BidPlacedEvent event);

    void publishClosed(AuctionClosedEvent event);
}
