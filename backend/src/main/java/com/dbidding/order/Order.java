package com.dbidding.order;

import com.dbidding.order.exception.InvalidOrderStatusException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
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

    @Column(nullable = false)
    private long price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private Order(Integer auctionId, Integer buyerId, Integer sellerId, long price) {
        this.auctionId = auctionId;
        this.buyerId = buyerId;
        this.sellerId = sellerId;
        this.price = price;
        this.status = OrderStatus.PENDING_CONFIRM;
    }

    public static Order pendingConfirm(Integer auctionId, Integer buyerId, Integer sellerId, long price) {
        return new Order(auctionId, buyerId, sellerId, price);
    }

    public void confirm() {
        requirePendingConfirm();
        this.status = OrderStatus.COMPLETED;
    }

    public void cancel() {
        requirePendingConfirm();
        this.status = OrderStatus.CANCELLED;
    }

    private void requirePendingConfirm() {
        if (status != OrderStatus.PENDING_CONFIRM) {
            throw new InvalidOrderStatusException();
        }
    }
}
