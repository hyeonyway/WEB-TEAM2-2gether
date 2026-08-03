package com.dbidding.card.port;

import java.util.List;

public interface CardWishlistPort {
    int countWishlists(Integer cardId);

    List<Integer> findCardIdsByUserId(Integer userId);
}
