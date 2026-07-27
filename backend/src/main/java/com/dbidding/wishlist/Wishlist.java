package com.dbidding.wishlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "wishlists")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wishlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "item_id", nullable = false)
    private Integer cardId;

    private Wishlist(Integer userId, Integer cardId) {
        this.userId = userId;
        this.cardId = cardId;
    }

    public static Wishlist of(Integer userId, Integer cardId) {
        return new Wishlist(userId, cardId);
    }
}
