package com.dbidding.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class CardResponses {
    private CardResponses() {
    }

    public record CardSummary(
            Integer id,
            String name,
            @JsonProperty("market_price") long marketPrice,
            @JsonProperty("low_price") long lowPrice,
            @JsonProperty("high_price") long highPrice,
            @JsonProperty("change_rate") BigDecimal changeRate,
            String theme,
            @JsonProperty("bid_count") int bidCount,
            @JsonProperty("psa_grade") String psaGrade,
            String language,
            @JsonProperty("thumbnail_url") String thumbnailUrl
    ) {
    }

    public record Page<T>(
            List<T> content,
            int page,
            int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("has_next") boolean hasNext
    ) {
    }

    public record CardDetail(
            Integer id,
            String name,
            @JsonProperty("set_name") String setName,
            String rarity,
            @JsonProperty("market_price") long marketPrice,
            @JsonProperty("low_price") long lowPrice,
            @JsonProperty("high_price") long highPrice,
            @JsonProperty("average_price") long averagePrice,
            @JsonProperty("change_rate") BigDecimal changeRate,
            @JsonProperty("weekly_change_rate") BigDecimal weeklyChangeRate,
            @JsonProperty("monthly_change_rate") BigDecimal monthlyChangeRate,
            @JsonProperty("bid_count") int bidCount,
            @JsonProperty("active_auction_count") int activeAuctionCount,
            @JsonProperty("wishlist_count") int wishlistCount,
            @JsonProperty("psa_grade") String psaGrade,
            String language,
            @JsonProperty("image_url") String imageUrl,
            List<PricePoint> history
    ) {
    }

    public record PricePoint(
            LocalDateTime date,
            @JsonProperty("average_price") long averagePrice,
            @JsonProperty("change_rate") BigDecimal changeRate,
            @JsonProperty("weekly_change_rate") BigDecimal weeklyChangeRate,
            @JsonProperty("monthly_change_rate") BigDecimal monthlyChangeRate
    ) {
    }
}
