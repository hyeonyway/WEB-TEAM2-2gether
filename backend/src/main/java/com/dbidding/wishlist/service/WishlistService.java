package com.dbidding.wishlist.service;

import com.dbidding.wishlist.domain.Wishlist;
import com.dbidding.wishlist.exception.WishlistException;
import com.dbidding.wishlist.repository.WishlistRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    /**
     * 여러 카드의 찜 유저를 한 번의 조회로 가져와 cardId별로 그룹핑한다.
     * (알림 복구 배치가 경매마다 {@link #findUserIdsByCardId}를 호출하는 N+1을 피하기 위함)
     */
    public Map<Integer, List<Integer>> groupUserIdsByCardIdIn(Collection<Integer> cardIds) {
        return wishlistRepository.findByCardIdIn(cardIds).stream()
                .collect(Collectors.groupingBy(
                        Wishlist::getCardId,
                        Collectors.mapping(Wishlist::getUserId, Collectors.toList())
                ));
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
