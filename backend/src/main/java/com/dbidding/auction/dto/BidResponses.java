package com.dbidding.auction.dto;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

public final class BidResponses {
    private BidResponses() {
    }

    @Builder
    public record BidContext(
            Integer auctionId,
            AuctionStatus status,
            Long version,
            Long currentPrice,
            Long minimumBid,
            Long bidIncrement,
            MyBidStatus myBidStatus,
            Long myBidAmount,
            WalletSummary wallet,
            List<BidSummary> recentBids
    ) {
    }

    public record WalletSummary(
            long availableBalance,
            long frozenBalance
    ) {
    }

    @Builder
    public record BidSummary(
            Long id,
            Long amount,
            String bidderAlias,
            boolean isHighest,
            LocalDateTime createdAt
    ) {
    }
}
