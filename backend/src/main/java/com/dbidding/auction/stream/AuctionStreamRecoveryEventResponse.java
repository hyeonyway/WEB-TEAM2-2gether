package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionBidEventInbox;
import java.time.Instant;

public record AuctionStreamRecoveryEventResponse(
        String streamId,
        Integer auctionId,
        String eventType,
        String projectionStatus,
        int attemptCount,
        Instant occurredAt,
        Instant lastAttemptAt,
        String failureMessage
) {
    static AuctionStreamRecoveryEventResponse from(AuctionBidEventInbox inbox) {
        return new AuctionStreamRecoveryEventResponse(
                inbox.getStreamId(), inbox.getAuctionId(), inbox.getEventType(), inbox.getProjectionStatus().name(),
                inbox.getAttemptCount(), inbox.getOccurredAt(), inbox.getLastAttemptAt(), inbox.getFailureMessage()
        );
    }
}
