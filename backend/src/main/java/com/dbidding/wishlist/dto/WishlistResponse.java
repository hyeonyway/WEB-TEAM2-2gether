package com.dbidding.wishlist.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.dbidding.wishlist.domain.Wishlist;

public record WishlistResponse(
        Integer id,
        @JsonProperty("card_id") Integer cardId
) {

    public static WishlistResponse from(Wishlist wishlist) {
        return new WishlistResponse(wishlist.getId(), wishlist.getCardId());
    }
}
