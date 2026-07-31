package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class BidResponses {
    private BidResponses() {
    }

    @Builder
    public record BidContext(
            @JsonProperty("auction_id") Integer auctionId,
            AuctionStatus status,
            Long version,
            @JsonProperty("current_price") Long currentPrice,
            @JsonProperty("minimum_bid") Long minimumBid,
            @JsonProperty("bid_increment") Long bidIncrement,
            @JsonProperty("my_bid_status") MyBidStatus myBidStatus,
            @JsonProperty("my_bid_amount") Long myBidAmount,
            WalletSummary wallet,
            @JsonProperty("recent_bids") List<BidSummary> recentBids
    ) {
    }

    public record WalletSummary(
            @JsonProperty("available_balance") long availableBalance,
            @JsonProperty("frozen_balance") long frozenBalance
    ) {
    }

    @Builder
    public record BidSummary(
            Long id,
            Long amount,
            @JsonProperty("bidder_alias") String bidderAlias,
            @JsonProperty("is_highest") boolean isHighest,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
    }

    public record BidResult(
            BidDetail bid,
            AuctionSnapshot auction,
            WalletSummary wallet
    ) {
    }

    public record BidDetail(
            Long id,
            Long amount,
            BidStatus status,
            @JsonProperty("created_at") LocalDateTime createdAt
    ) {
    }

    public record AuctionSnapshot(
            Integer id,
            Long version,
            @JsonProperty("current_price") Long currentPrice,
            @JsonProperty("minimum_bid") Long minimumBid,
            @JsonProperty("bid_count") Integer bidCount,
            @JsonProperty("ends_at") LocalDateTime endsAt
    ) {
    }
}
