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

    @Column(name = "auction_id", nullable = false)
    private Integer auctionId;

    @Column(name = "auction_version", nullable = false)
    private Long auctionVersion;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    public AuctionBidEventInbox(String streamId, Integer auctionId, Long auctionVersion, Instant processedAt) {
        this.streamId = streamId;
        this.auctionId = auctionId;
        this.auctionVersion = auctionVersion;
        this.processedAt = processedAt;
    }
}
