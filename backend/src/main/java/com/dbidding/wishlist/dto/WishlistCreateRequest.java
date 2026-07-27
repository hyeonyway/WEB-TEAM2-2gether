package com.dbidding.wishlist.dto;

import jakarta.validation.constraints.NotNull;

public record WishlistCreateRequest(
        @NotNull Integer cardId
) {
}
