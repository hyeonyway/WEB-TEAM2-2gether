package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionSort;
import java.time.Instant;

public record AuctionCursor(
        AuctionSort sort,
        Long value,
        Instant timeValue,
        Integer auctionId
) {
}
