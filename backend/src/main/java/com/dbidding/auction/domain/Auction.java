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
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Formula;

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

    @Formula("floor((current_price - start_price) * 10000.0 / start_price)")
    private Long changeRateBasisPoints;

    @Column(name = "buy_now_price")
    private Long buyNowPrice;

    @Column(name = "seller_memo", length = 1000)
    private String sellerMemo;

    @Column(name = "psa_certification", length = 32)
    private String psaCertification;

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

    @Column(name = "idempotency_key", length = 64)
    private String createIdempotencyKey;

    @Column(name = "idempotency_request_hash", length = 64)
    private String createIdempotencyRequestHash;

    @Builder
    public Auction(
            Integer sellerId,
            Integer itemId,
            String auctionName,
            String description,
            String sellerMemo,
            String psaCertification,
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
        this.sellerMemo = sellerMemo;
        this.psaCertification = psaCertification;
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

    public void recordCreateIdempotency(String idempotencyKey, String requestHash) {
        this.createIdempotencyKey = idempotencyKey;
        this.createIdempotencyRequestHash = requestHash;
    }

    public Long minimumBid() {
        return currentPrice + bidPriceUnit;
    }

    public void closeWithWinningBid(Bid winningBid, LocalDateTime closedAt) {
        validateClosable();
        if (winningBid == null) {
            throw new IllegalArgumentException("낙찰 입찰이 필요합니다.");
        }
        status = AuctionStatus.ENDED;
        currentPrice = winningBid.getBidPrice();
        closeTime = closedAt;
    }

    public void closeWithoutTrade(LocalDateTime closedAt) {
        validateClosable();
        status = AuctionStatus.FAILED;
        closeTime = closedAt;
    }

    private void validateClosable() {
        if (status != AuctionStatus.OPEN && status != AuctionStatus.ENDING) {
            throw new IllegalArgumentException("진행 중인 경매만 종료할 수 있습니다.");
        }
    }

    private boolean extendCloseTimeIfNeeded(
            LocalDateTime bidAt,
            Duration extensionWindow,
            Duration extensionDuration
    ) {
        if (extensionWindow.isNegative() || extensionWindow.isZero()) {
            return false;
        }
        if (extensionDuration.isNegative() || extensionDuration.isZero()) {
            return false;
        }
        LocalDateTime extensionThreshold = closeTime.minus(extensionWindow);
        if (bidAt.isBefore(extensionThreshold)) {
            return false;
        }
        LocalDateTime extendedCloseTime = closeTime.plus(extensionDuration);
        closeTime = extendedCloseTime;
        estimatedCloseTime = extendedCloseTime;
        status = AuctionStatus.ENDING;
        return true;
    }

    public boolean placeBid(
            Long bidPrice,
            LocalDateTime bidAt,
            Duration extensionWindow,
            Duration extensionDuration
    ) {
        if (status != AuctionStatus.OPEN && status != AuctionStatus.ENDING) {
            throw new IllegalArgumentException("진행 중인 경매에만 입찰할 수 있습니다.");
        }
        if (!bidAt.isBefore(closeTime)) {
            throw new IllegalArgumentException("이미 종료된 경매입니다.");
        }
        if (bidPrice < minimumBid()) {
            throw new IllegalArgumentException("최소 입찰가 이상으로 입찰해야 합니다.");
        }
        currentPrice = bidPrice;
        bidCount++;
        return extendCloseTimeIfNeeded(bidAt, extensionWindow, extensionDuration);
    }
}
