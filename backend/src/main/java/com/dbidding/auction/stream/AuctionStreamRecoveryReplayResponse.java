package com.dbidding.auction.stream;

public record AuctionStreamRecoveryReplayResponse(
        boolean accepted,
        String replayFromStreamId,
        long pendingCount,
        String message
) {
}
