package com.dbidding.wishlist.dto;

import com.dbidding.wishlist.Wishlist;

public record WishlistResponse(
        Integer id,
        Integer cardId
) {

    public static WishlistResponse from(Wishlist wishlist) {
        return new WishlistResponse(wishlist.getId(), wishlist.getCardId());
    }
}
