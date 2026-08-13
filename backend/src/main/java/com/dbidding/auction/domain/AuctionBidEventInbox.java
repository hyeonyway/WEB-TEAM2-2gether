package com.dbidding.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "auction_bid_event_inbox")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuctionBidEventInbox {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stream_id", nullable = false, unique = true, length = 64)
    private String streamId;

    @Column(name = "auction_id")
    private Integer auctionId;

    @Column(name = "auction_version")
    private Long auctionVersion;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "schema_version", nullable = false)
    private Integer schemaVersion;

    @Column(name = "payload", nullable = false, columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "projection_status", nullable = false, length = 16)
    private AuctionBidEventProjectionStatus projectionStatus;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_attempt_at")
    private Instant lastAttemptAt;

    public AuctionBidEventInbox(
            String streamId,
            Integer auctionId,
            Long auctionVersion,
            String eventType,
            Integer schemaVersion,
            String payload,
            Instant occurredAt,
            Instant recordedAt
    ) {
        this.streamId = streamId;
        this.auctionId = auctionId;
        this.auctionVersion = auctionVersion;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.projectionStatus = AuctionBidEventProjectionStatus.PENDING;
        this.attemptCount = 0;
        this.processedAt = null;
    }

    public void markProcessed(Instant processedAt) {
        this.projectionStatus = AuctionBidEventProjectionStatus.PROCESSED;
        this.failureMessage = null;
        this.processedAt = processedAt;
    }

    public void markError(String failureMessage) {
        this.projectionStatus = AuctionBidEventProjectionStatus.ERROR;
        this.failureMessage = failureMessage;
        this.processedAt = null;
    }

    public void recordAttempt(Instant attemptedAt) {
        this.attemptCount++;
        this.lastAttemptAt = attemptedAt;
    }
}
