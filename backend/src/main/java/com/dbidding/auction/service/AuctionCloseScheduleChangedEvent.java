package com.dbidding.auction.service;

import java.time.LocalDateTime;

public record AuctionCloseScheduleChangedEvent(
        Integer auctionId,
        LocalDateTime closeTime,
        String reason
) {
}
