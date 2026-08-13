package com.dbidding.auction.stream;

public record AuctionStreamRecoveryStatus(
        long pendingCount,
        long errorCount,
        long processedCount,
        String firstIncompleteStreamId,
        String firstFailureMessage,
        String latestProcessedStreamId,
        java.time.Instant latestProcessedAt
) {
}
