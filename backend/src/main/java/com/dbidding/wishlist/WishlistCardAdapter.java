package com.dbidding.wishlist;

import com.dbidding.card.port.CardWishlistPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class WishlistCardAdapter implements CardWishlistPort {
    private final WishlistRepository wishlistRepository;

    @Override
    public int countWishlists(Integer cardId) {
        return Math.toIntExact(wishlistRepository.countByCardId(cardId));
    }
}
