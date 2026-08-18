package com.dbidding.auction.bid;

import com.dbidding.auction.bid.dto.BidCommand;
import com.dbidding.auction.bid.dto.BidExecutionResult;

public interface BidExecutor {
    BidExecutionResult execute(BidCommand command);
}
