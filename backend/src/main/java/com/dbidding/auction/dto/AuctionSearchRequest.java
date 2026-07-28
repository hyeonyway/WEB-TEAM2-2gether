package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record AuctionSearchRequest(
        String keyword,
        String psaGrade,
        AuctionSort sort,
        AuctionStatus status,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size
) {
    public String keywordOrDefault() {
        return keyword == null ? "" : keyword.trim();
    }

    public AuctionSort sortOrDefault() {
        return sort == null ? AuctionSort.BID_COUNT : sort;
    }

    public AuctionStatus statusOrDefault() {
        return status == null ? AuctionStatus.OPEN : status;
    }

    public int pageOrDefault() {
        return page == null ? 0 : page;
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
