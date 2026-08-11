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

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public AuctionBidEventInbox(
            String streamId,
            Integer auctionId,
            Long auctionVersion,
            String eventType,
            Integer schemaVersion,
            String payload,
            Instant occurredAt,
            Instant processedAt
    ) {
        this.streamId = streamId;
        this.auctionId = auctionId;
        this.auctionVersion = auctionVersion;
        this.eventType = eventType;
        this.schemaVersion = schemaVersion;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.processedAt = processedAt;
    }
}
