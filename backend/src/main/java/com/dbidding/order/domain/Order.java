package com.dbidding.order.domain;

import com.dbidding.order.exception.InvalidOrderStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "auction_id", nullable = false)
    private Integer auctionId;

    @Column(name = "buyer_id", nullable = false)
    private Integer buyerId;

    @Column(name = "seller_id", nullable = false)
    private Integer sellerId;

    @Column(name = "card_name", nullable = false, length = 200)
    private String cardName;

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    private Order(Integer auctionId, Integer buyerId, Integer sellerId, String cardName, long price) {
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.cardName = cardName;
        this.price = price;
        this.status = OrderStatus.PENDING_CONFIRM;
    }

    public static Order pendingConfirm(Integer auctionId, Integer buyerId, Integer sellerId, String cardName, long price) {
        return new Order(auctionId, buyerId, sellerId, cardName, price);
    }

    public void confirm() {
        requirePendingConfirm();
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        requirePendingConfirm();
        this.status = OrderStatus.CANCELLED;
    }

    /** Redis 승인 이벤트를 MySQL projection에 반영한다. */
    public void applyProjectedStatus(OrderStatus status) {
        if (this.status == status) return;
        requirePendingConfirm();
        if (status == OrderStatus.COMPLETED) confirm();
        else if (status == OrderStatus.CANCELLED) cancel();
        else throw new InvalidOrderStatusException();
    }

    private void requirePendingConfirm() {
        if (status != OrderStatus.PENDING_CONFIRM) {
            throw new InvalidOrderStatusException();
        }
    }
}
