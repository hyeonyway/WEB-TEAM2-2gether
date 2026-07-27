package com.dbidding.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "auction_id", nullable = false)
    private Integer auctionId;

    @Column(nullable = false)
    private String message;

    private Notification(Integer userId, Integer auctionId, String message) {
        this.userId = userId;
        this.auctionId = auctionId;
        this.message = message;
    }

    public static Notification of(Integer userId, Integer auctionId, String message) {
        return new Notification(userId, auctionId, message);
    }
}
