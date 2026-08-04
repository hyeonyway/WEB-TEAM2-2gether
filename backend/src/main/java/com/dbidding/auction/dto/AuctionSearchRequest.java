package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record AuctionSearchRequest(
        String keyword,
        String psaGrade,
        AuctionSort sort,
        AuctionStatus status,
        String cursor,
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

    public List<AuctionStatus> statusesOrDefault() {
        if (status != null) {
            return List.of(status);
        }
        return List.of(AuctionStatus.OPEN, AuctionStatus.ENDING);
    }

    public int sizeOrDefault() {
        return size == null ? 20 : size;
    }
}
