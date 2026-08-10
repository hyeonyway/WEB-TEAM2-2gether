package com.dbidding.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
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

    @Column(name = "self_grade", length = 32)
    private String selfGrade;

    @Column(name = "psa_verified", nullable = false)
    private Boolean psaVerified;

    @Column(name = "delivery_fee", nullable = false)
    private Long deliveryFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuctionStatus status;

    @Column(name = "open_time", nullable = false)
    private Instant openTime;

    @Column(name = "estimated_close_time", nullable = false)
    private Instant estimatedCloseTime;

    @Column(name = "close_time", nullable = false)
    private Instant closeTime;

    @Column(name = "bid_count", nullable = false)
    private Integer bidCount;

    @Column(name = "bid_price_unit", nullable = false)
    private Long bidPriceUnit;

    @Column(name = "last_bid_event_version", nullable = false)
    private Long lastBidEventVersion;

    @Column(name = "is_hyped", nullable = false)
    private Boolean hyped;

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
            String selfGrade,
            Boolean psaVerified,
            Long startPrice,
            Long buyNowPrice,
            Long deliveryFee,
            Instant openTime,
            Instant estimatedCloseTime,
            Instant closeTime,
            Long bidPriceUnit,
            Boolean hyped
    ) {
        this.sellerId = sellerId;
        this.itemId = itemId;
        this.auctionName = auctionName;
        this.description = description;
        this.sellerMemo = sellerMemo;
        this.psaCertification = psaCertification;
        this.selfGrade = selfGrade;
        this.psaVerified = psaVerified == null ? Boolean.FALSE : psaVerified;
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
        this.lastBidEventVersion = 0L;
        this.hyped = hyped == null ? Boolean.FALSE : hyped;
    }

    public void recordCreateIdempotency(String idempotencyKey, String requestHash) {
        this.createIdempotencyKey = idempotencyKey;
        this.createIdempotencyRequestHash = requestHash;
    }

    public Long minimumBid() {
        long nextBid = currentPrice + bidPriceUnit;
        return buyNowPrice == null ? nextBid : Math.min(nextBid, buyNowPrice);
    }

    public void closeWithWinningBid(Bid winningBid, Instant closedAt) {
        validateClosable();
        if (winningBid == null) {
            throw new IllegalArgumentException("낙찰 입찰이 필요합니다.");
        }
        status = AuctionStatus.ENDED;
        currentPrice = winningBid.getBidPrice();
        closeTime = closedAt;
    }

    public void closeWithoutTrade(Instant closedAt) {
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
            Instant bidAt,
            Duration extensionWindow,
            Duration extensionDuration
    ) {
        if (extensionWindow.isNegative() || extensionWindow.isZero()) {
            return false;
        }
        if (extensionDuration.isNegative() || extensionDuration.isZero()) {
            return false;
        }
        Instant extensionThreshold = closeTime.minus(extensionWindow);
        if (bidAt.isBefore(extensionThreshold)) {
            return false;
        }
        Instant extendedCloseTime = closeTime.plus(extensionDuration);
        closeTime = extendedCloseTime;
        estimatedCloseTime = extendedCloseTime;
        status = AuctionStatus.ENDING;
        return true;
    }

    public boolean placeBid(
            Long bidPrice,
            Instant bidAt,
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

    public boolean applyStreamBid(
            long auctionVersion,
            long currentPrice,
            int bidCount,
            Instant closeTime,
            AuctionStatus status
    ) {
        if (auctionVersion <= lastBidEventVersion) {
            return false;
        }
        this.currentPrice = currentPrice;
        this.bidCount = bidCount;
        this.closeTime = closeTime;
        this.estimatedCloseTime = closeTime;
        this.status = status;
        this.lastBidEventVersion = auctionVersion;
        return true;
    }

    public boolean isNextBidEventVersion(long auctionVersion) {
        return auctionVersion == lastBidEventVersion + 1;
    }

    public void validateStreamBid(
            Integer bidderId,
            long bidPrice,
            long currentPrice,
            int bidCount,
            Instant closeTime,
            Instant occurredAt,
            AuctionStatus incomingStatus,
            boolean buyNow
    ) {
        if (sellerId.equals(bidderId)) {
            throw new IllegalArgumentException("판매자는 자신의 경매에 입찰할 수 없습니다.");
        }
        if (bidCount != this.bidCount + 1) {
            throw new IllegalArgumentException("입찰 수가 이전 경매 상태와 일치하지 않습니다.");
        }
        if (bidPrice != currentPrice) {
            throw new IllegalArgumentException("입찰가와 현재가는 일치해야 합니다.");
        }
        if (!occurredAt.isBefore(this.closeTime)) {
            throw new IllegalArgumentException("이미 종료된 경매입니다.");
        }
        if (buyNow) {
            if (buyNowPrice == null || bidPrice != buyNowPrice || incomingStatus != AuctionStatus.ENDED) {
                throw new IllegalArgumentException("즉시 낙찰 이벤트의 최종 상태가 올바르지 않습니다.");
            }
            return;
        }
        if ((status != AuctionStatus.OPEN && status != AuctionStatus.ENDING)
                || (incomingStatus != AuctionStatus.OPEN && incomingStatus != AuctionStatus.ENDING)) {
            throw new IllegalArgumentException("진행 중인 경매 입찰 이벤트만 처리할 수 있습니다.");
        }
        if (bidPrice < minimumBid()) {
            throw new IllegalArgumentException("최소 입찰가 이상으로 입찰해야 합니다.");
        }
        if (closeTime.isBefore(this.closeTime)) {
            throw new IllegalArgumentException("일반 입찰은 경매 마감 시각을 앞당길 수 없습니다.");
        }
    }
}
