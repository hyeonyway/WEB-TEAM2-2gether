package com.dbidding.dashboard.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

public final class DashboardResponse {
    private DashboardResponse() {
    }

    public record AuctionSnapshot(
            Integer id,
            CardSnapshot card,
            @JsonProperty("start_price") Long startPrice,
            @JsonProperty("current_price") Long currentPrice,
            @JsonProperty("bid_increment") Long bidIncrement,
            @JsonProperty("bid_count") Integer bidCount,
            @JsonProperty("ends_at") LocalDateTime endsAt,
            AuctionStatus status,
            Long version,
            @JsonProperty("my_bid_status") MyBidStatus myBidStatus,
            @JsonProperty("my_bid_amount") Long myBidAmount
    ) {
    }

    public record CardSnapshot(
            Integer id,
            String name,
            @JsonProperty("psa_grade") String psaGrade,
            String language,
            @JsonProperty("thumbnail_url") String thumbnailUrl
    ) {
    }
}
