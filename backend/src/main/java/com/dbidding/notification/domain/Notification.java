package com.dbidding.notification.domain;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    /** bid와 무관한 알림 타입(AUCTION_OPENED/WON/UNSOLD)에 쓰는 sentinel — NULL 대신 0을 써야
     * (user_id, auction_id, type, bid_id) 유니크 제약이 이 타입들에도 제대로 걸린다. */
    public static final Long NO_BID = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "auction_id", nullable = false)
    private Integer auctionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "bid_id", nullable = false)
    private Long bidId;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Notification(Integer userId, Integer auctionId, NotificationType type, Long bidId, String message) {
        this.userId = userId;
        this.auctionId = auctionId;
        this.type = type;
        this.bidId = bidId;
        this.message = message;
    }

    public static Notification of(Integer userId, Integer auctionId, NotificationType type, String message) {
        return new Notification(userId, auctionId, type, NO_BID, message);
    }

    public static Notification ofBid(Integer userId, Integer auctionId, NotificationType type, Long bidId, String message) {
        return new Notification(userId, auctionId, type, bidId, message);
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
