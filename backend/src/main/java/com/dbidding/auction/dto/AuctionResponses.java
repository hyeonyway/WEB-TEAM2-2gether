package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;
import lombok.Builder;

public final class AuctionResponses {
    private AuctionResponses() {
    }

    public record Page<T>(
            List<T> content,
            int page,
            int size,
            @JsonProperty("total_elements") long totalElements,
            @JsonProperty("has_next") boolean hasNext
    ) {
    }

    public record CursorPage<T>(
            List<T> content,
            @JsonProperty("next_cursor") String nextCursor,
            @JsonProperty("has_next") boolean hasNext,
            @JsonProperty("total_elements") long totalElements
    ) {
    }

    @Builder
    public record AuctionSummary(
            Integer id,
            CardSummary card,
            SellerSummary seller,
            @JsonProperty("start_price") Long startPrice,
            @JsonProperty("current_price") Long currentPrice,
            @JsonProperty("bid_increment") Long bidIncrement,
            @JsonProperty("minimum_bid") Long minimumBid,
            @JsonProperty("bid_count") Integer bidCount,
            @JsonProperty("buy_now_price") Long buyNowPrice,
            @JsonProperty("starts_at") Instant startsAt,
            @JsonProperty("ends_at") Instant endsAt,
            AuctionStatus status,
            @JsonProperty("my_bid_status") MyBidStatus myBidStatus,
            @JsonProperty("my_bid_amount") Long myBidAmount
    ) {
    }

    @Builder
    public record AuctionDetail(
            Integer id,
            CardSummary card,
            SellerSummary seller,
            @JsonProperty("start_price") Long startPrice,
            @JsonProperty("current_price") Long currentPrice,
            @JsonProperty("bid_increment") Long bidIncrement,
            @JsonProperty("minimum_bid") Long minimumBid,
            @JsonProperty("bid_count") Integer bidCount,
            @JsonProperty("starts_at") Instant startsAt,
            @JsonProperty("ends_at") Instant endsAt,
            AuctionStatus status,
            @JsonProperty("my_bid_status") MyBidStatus myBidStatus,
            @JsonProperty("my_bid_amount") Long myBidAmount,
            String description,
            @JsonProperty("seller_memo") String sellerMemo,
            @JsonProperty("seller_grade") String sellerGrade,
            @JsonProperty("shipping_fee") Long shippingFee,
            @JsonProperty("buy_now_price") Long buyNowPrice,
            List<AuctionPhoto> photos,
            @JsonProperty("psa_certification") PsaCertification psaCertification
    ) {
    }

    public record CardSummary(
            Integer id,
            String name,
            @JsonProperty("set_name") String setName,
            @JsonProperty("psa_grade") String psaGrade,
            String language,
            @JsonProperty("thumbnail_url") String thumbnailUrl
    ) {
    }

    public record SellerSummary(
            Integer id,
            String nickname,
            @JsonProperty("trade_count") Integer tradeCount,
            @JsonProperty("trust_score") Integer trustScore
    ) {
    }

    public record DashboardAuction(
            Integer id,
            Integer sellerId,
            CardSummary card,
            Long startPrice,
            Long currentPrice,
            Long bidIncrement,
            Integer bidCount,
            Instant estimatedCloseTime,
            Instant closeTime,
            AuctionStatus status,
            BidStatus bidStatus,
            Long bidAmount
    ) {
    }

    public record FailedAuctionSummary(
            Integer id,
            @JsonProperty("card_name") String cardName,
            @JsonProperty("start_price") Long startPrice,
            @JsonProperty("closed_at") Instant closedAt
    ) {
    }

    public record AuctionPhoto(
            Integer id,
            String url,
            int order,
            boolean representative
    ) {
    }

    public record PsaCertification(
            @JsonProperty("certification_number") String certificationNumber,
            String grade,
            Integer population,
            boolean verified
    ) {
    }

}
