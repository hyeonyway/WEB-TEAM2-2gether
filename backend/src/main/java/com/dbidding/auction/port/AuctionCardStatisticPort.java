package com.dbidding.auction.port;

import java.time.LocalDateTime;

public interface AuctionCardStatisticPort {
    void recordAuctionOpened(Integer itemId, LocalDateTime date);

    void recordBid(Integer itemId, LocalDateTime date);

    void recordAuctionCompleted(Integer itemId, long winningPrice, LocalDateTime date);

    void recordAuctionClosedWithoutTrade(Integer itemId, LocalDateTime date);
}
