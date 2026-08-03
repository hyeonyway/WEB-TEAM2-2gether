package com.dbidding.wishlist;

import java.util.List;

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

    @Override
    public List<Integer> findCardIdsByUserId(Integer userId) {
        return wishlistRepository.findByUserId(userId).stream()
                .map(Wishlist::getCardId)
                .toList();
    }
}
