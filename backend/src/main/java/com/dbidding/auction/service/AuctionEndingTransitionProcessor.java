package com.dbidding.auction.service;

import java.time.Instant;
import java.util.List;

public interface AuctionEndingTransitionProcessor {
    List<Integer> transitionDueAuctions(Instant now, int limit);
}
