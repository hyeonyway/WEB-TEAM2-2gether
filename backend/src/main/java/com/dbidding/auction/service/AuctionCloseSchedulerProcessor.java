package com.dbidding.auction.service;

import java.time.Instant;
import java.util.List;

/** Schedulers dispatch closing work to the profile-specific closing path. */
public interface AuctionCloseSchedulerProcessor {
    List<Integer> processDueAuctions(Instant now, int limit);
}
