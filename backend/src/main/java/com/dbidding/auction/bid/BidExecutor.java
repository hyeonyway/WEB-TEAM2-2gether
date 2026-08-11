package com.dbidding.auction.bid;

public interface BidExecutor {
    BidExecutionResult execute(BidCommand command);
}
