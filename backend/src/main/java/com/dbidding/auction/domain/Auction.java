package com.dbidding.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "auctions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Auction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer sellerId;

    @Column(name = "item_id", nullable = false)
    private Integer itemId;

    @Column(name = "auction_name", nullable = false)
    private String auctionName;

    @Column(nullable = false)
    private String description;

    @Column(name = "start_price", nullable = false)
    private Long startPrice;

    @Column(name = "current_price", nullable = false)
    private Long currentPrice;

    @Column(name = "buy_now_price", nullable = false)
    private Long buyNowPrice;

    @Column(name = "delivery_fee", nullable = false)
    private Long deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(name = "open_time", nullable = false)
    private LocalDateTime openTime;

    @Column(name = "estimated_close_time", nullable = false)
    private LocalDateTime estimatedCloseTime;

    @Column(name = "close_time", nullable = false)
    private LocalDateTime closeTime;

    @Column(name = "bid_count", nullable = false)
    private Integer bidCount;

    @Column(name = "bid_price_unit", nullable = false)
    private Long bidPriceUnit;

    @Column(name = "is_hyped", nullable = false)
    private Boolean hyped;

    @Version
    @Column(nullable = false)
    private Long version;

    @Builder
    public Auction(
            Integer sellerId,
            Integer itemId,
            String auctionName,
            String description,
            Long startPrice,
            Long buyNowPrice,
            Long deliveryFee,
            LocalDateTime openTime,
            LocalDateTime estimatedCloseTime,
            LocalDateTime closeTime,
            Long bidPriceUnit,
            Boolean hyped
    ) {
        this.sellerId = sellerId;
        this.itemId = itemId;
        this.auctionName = auctionName;
        this.description = description;
        this.startPrice = startPrice;
        this.currentPrice = startPrice;
        this.buyNowPrice = buyNowPrice;
        this.deliveryFee = deliveryFee;
        this.status = AuctionStatus.OPEN;
        this.openTime = openTime;
        this.estimatedCloseTime = estimatedCloseTime;
        this.closeTime = closeTime;
        this.bidCount = 0;
        this.bidPriceUnit = bidPriceUnit;
        this.hyped = hyped == null ? Boolean.FALSE : hyped;
        this.version = 1L;
    }

    public Long minimumBid() {
        return currentPrice + bidPriceUnit;
    }
}
