package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class AuctionResponses {
    private AuctionResponses() {
    }

    public record Page<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            boolean hasNext
    ) {
    }

    @Builder
    public record AuctionSummary(
            Integer id,
            CardSummary card,
            SellerSummary seller,
            Long startPrice,
            Long currentPrice,
            Long bidIncrement,
            Long minimumBid,
            Integer bidCount,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            AuctionStatus status,
            Long version,
            MyBidStatus myBidStatus,
            Long myBidAmount
    ) {
    }

    @Builder
    public record AuctionDetail(
            Integer id,
            CardSummary card,
            SellerSummary seller,
            Long startPrice,
            Long currentPrice,
            Long bidIncrement,
            Long minimumBid,
            Integer bidCount,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            AuctionStatus status,
            Long version,
            MyBidStatus myBidStatus,
            Long myBidAmount,
            String description,
            String sellerMemo,
            Long shippingFee,
            Long buyNowPrice,
            List<AuctionPhoto> photos,
            PsaCertification psaCertification
    ) {
    }

    public record CardSummary(
            Integer id,
            String name,
            String setName,
            String psaGrade,
            String language,
            String thumbnailUrl
    ) {
    }

    public record SellerSummary(
            Integer id,
            String nickname,
            Integer tradeCount,
            Integer trustScore
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
            String certificationNumber,
            String grade,
            Integer population,
            boolean verified
    ) {
    }

}
