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
import java.time.LocalDateTime;
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
    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BidStatus status;

    public static Bid leading(Integer bidderId, Auction auction, Long bidPrice, LocalDateTime createdAt) {
        return Bid.builder()
                .bidderId(bidderId)
                .auction(auction)
                .bidPrice(bidPrice)
                .createdAt(createdAt)
                .status(BidStatus.LEADING)
                .build();
    }

    @Builder
    public Bid(Integer bidderId, Auction auction, Long bidPrice, LocalDateTime createdAt, BidStatus status) {
        this.bidderId = bidderId;
        this.auction = auction;
        this.bidPrice = bidPrice;
        this.createdAt = createdAt;
        this.status = status;
    }

    public void markOutbid() {
        status = BidStatus.OUTBID;
    }
}
