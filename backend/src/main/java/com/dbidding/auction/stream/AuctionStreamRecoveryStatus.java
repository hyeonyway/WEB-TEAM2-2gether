package com.dbidding.auction.stream;

public record AuctionStreamRecoveryStatus(
        boolean paused,
        long pendingCount,
        long errorCount,
        String firstIncompleteStreamId,
        String firstFailureMessage
) {
}
