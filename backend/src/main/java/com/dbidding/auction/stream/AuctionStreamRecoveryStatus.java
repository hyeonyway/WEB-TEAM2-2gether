package com.dbidding.auction.stream;

public record AuctionStreamRecoveryStatus(
        long pendingCount,
        long errorCount,
        String firstIncompleteStreamId,
        String firstFailureMessage
) {
}
