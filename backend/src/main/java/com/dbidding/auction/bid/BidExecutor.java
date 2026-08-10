package com.dbidding.auction.bid;

import com.dbidding.auction.dto.BidResponses;

public interface BidExecutor {
    BidResponses.BidResult execute(BidCommand command);
}
