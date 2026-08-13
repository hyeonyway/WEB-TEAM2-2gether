package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionTimelineEvent;
import java.time.Instant;

public record AuctionStreamRecoveryEventResponse(
        String streamId,
        Integer auctionId,
        String eventType,
        String projectionStatus,
        int attemptCount,
        Instant occurredAt,
        Instant lastAttemptAt,
        Instant processedAt,
        String failureMessage
) {
    static AuctionStreamRecoveryEventResponse from(AuctionTimelineEvent inbox) {
        return new AuctionStreamRecoveryEventResponse(
                inbox.getStreamId(), inbox.getAuctionId(), inbox.getEventType(), inbox.getProjectionStatus().name(),
                inbox.getAttemptCount(), inbox.getOccurredAt(), inbox.getLastAttemptAt(), inbox.getProcessedAt(), inbox.getFailureMessage()
        );
    }
}
