package com.dbidding.wishlist;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 찜한 카드입니다.");
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
}
