package com.dbidding.auction.port;

import com.dbidding.auction.event.AuctionEvent;

public interface AuctionEventPort {
    void publish(AuctionEvent event);
}
