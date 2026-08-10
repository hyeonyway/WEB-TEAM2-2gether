package com.dbidding.wishlist;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WishlistService {

    private final WishlistRepository wishlistRepository;

    public WishlistService(WishlistRepository wishlistRepository) {
        this.wishlistRepository = wishlistRepository;
    }

    @Transactional
    public Wishlist add(Integer userId, Integer cardId) {
        if (wishlistRepository.existsByUserIdAndCardId(userId, cardId)) {
            throw WishlistException.alreadyExists();
        }
        return wishlistRepository.save(Wishlist.of(userId, cardId));
    }

    @Transactional
    public void remove(Integer userId, Integer cardId) {
        wishlistRepository.deleteByUserIdAndCardId(userId, cardId);
    }

    public List<Wishlist> findAll(Integer userId) {
        return wishlistRepository.findByUserId(userId);
    }

    public List<Integer> findUserIdsByCardId(Integer cardId) {
        return wishlistRepository.findByCardId(cardId).stream()
                .map(Wishlist::getUserId)
                .toList();
    }

    public int countWishlists(Integer cardId) {
        return Math.toIntExact(wishlistRepository.countByCardId(cardId));
    }

    public List<Integer> findCardIdsByUserId(Integer userId) {
        return wishlistRepository.findByUserId(userId).stream()
                .map(Wishlist::getCardId)
                .toList();
    }
}
