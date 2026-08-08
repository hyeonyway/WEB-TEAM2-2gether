package com.dbidding.auction.service;

import java.time.Instant;

public record AuctionCloseScheduleChangedEvent(
        Integer auctionId,
        Instant closeTime,
        String reason
) {
}
