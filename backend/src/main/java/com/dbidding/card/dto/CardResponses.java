package com.dbidding.card.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CardResponses {
    private CardResponses() {
    }

    public record CardSummary(
            Long id,
            String name,
            @JsonProperty("market_price") long marketPrice,
            @JsonProperty("change_rate") BigDecimal changeRate,
            String theme,
            @JsonProperty("bid_count") int bidCount,
            @JsonProperty("psa_grade") Integer psaGrade,
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
            Long id,
            String name,
            @JsonProperty("set_name") String setName,
            @JsonProperty("card_number") String cardNumber,
            String rarity,
            @JsonProperty("market_price") long marketPrice,
            @JsonProperty("low_price") long lowPrice,
            @JsonProperty("high_price") long highPrice,
            @JsonProperty("average_price") long averagePrice,
            @JsonProperty("change_rate") BigDecimal changeRate,
            @JsonProperty("weekly_change_rate") BigDecimal weeklyChangeRate,
            @JsonProperty("monthly_change_rate") BigDecimal monthlyChangeRate,
            @JsonProperty("trade_count") int tradeCount,
            @JsonProperty("bid_count") int bidCount,
            @JsonProperty("active_auction_count") int activeAuctionCount,
            @JsonProperty("favorite_count") int favoriteCount,
            @JsonProperty("psa_grade") Integer psaGrade,
            String language,
            @JsonProperty("image_url") String imageUrl,
            List<PricePoint> history
    ) {
    }

    public record PricePoint(
            LocalDate date,
            @JsonProperty("average_price") long averagePrice,
            @JsonProperty("trade_count") int tradeCount
    ) {
    }
}
