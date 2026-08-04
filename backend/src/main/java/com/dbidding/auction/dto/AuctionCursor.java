package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionSort;
import java.time.LocalDateTime;

public record AuctionCursor(
        AuctionSort sort,
        Long value,
        LocalDateTime timeValue,
        Integer auctionId
) {
}
