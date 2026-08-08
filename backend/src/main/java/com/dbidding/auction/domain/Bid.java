package com.dbidding.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "bids")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bid {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer bidderId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "auction_id", nullable = false)
    private Auction auction;

    @Column(name = "bid_price", nullable = false)
    private Long bidPrice;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(name = "idempotency_request_hash", length = 64)
    private String idempotencyRequestHash;

    public static Bid leading(Integer bidderId, Auction auction, Long bidPrice, Instant createdAt) {
        return leading(bidderId, auction, bidPrice, createdAt, null, null);
    }

    public static Bid leading(
            Integer bidderId,
            Auction auction,
            Long bidPrice,
            Instant createdAt,
            String idempotencyKey,
            String idempotencyRequestHash
    ) {
        return Bid.builder()
                .bidderId(bidderId)
                .auction(auction)
                .bidPrice(bidPrice)
                .createdAt(createdAt)
                .status(BidStatus.LEADING)
                .idempotencyKey(idempotencyKey)
                .idempotencyRequestHash(idempotencyRequestHash)
                .build();
    }

    public Bid(Integer bidderId, Auction auction, Long bidPrice, Instant createdAt, BidStatus status) {
        this(bidderId, auction, bidPrice, createdAt, status, null, null);
    }

    @Builder
    public Bid(
            Integer bidderId,
            Auction auction,
            Long bidPrice,
            Instant createdAt,
            BidStatus status,
            String idempotencyKey,
            String idempotencyRequestHash
    ) {
        this.bidderId = bidderId;
        this.auction = auction;
        this.bidPrice = bidPrice;
        this.createdAt = createdAt;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.idempotencyRequestHash = idempotencyRequestHash;
    }


    public void markOutbid() {
        if (status == BidStatus.LEADING) {
            status = BidStatus.OUTBID;
        }
    }

    public void markWon() {
        if (status != BidStatus.LEADING) {
            throw new IllegalArgumentException("최고 입찰만 낙찰 처리할 수 있습니다.");
        }
        status = BidStatus.WON;
    }
}
