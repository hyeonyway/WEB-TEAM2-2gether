package com.dbidding.auction.service;

import java.time.Duration;

public final class AuctionEndingPolicy {
    public static final Duration WINDOW = Duration.ofMinutes(5);

    private AuctionEndingPolicy() {
    }
}
