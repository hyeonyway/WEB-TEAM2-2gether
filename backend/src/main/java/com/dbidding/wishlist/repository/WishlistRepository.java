package com.dbidding.wishlist.repository;

import com.dbidding.wishlist.domain.Wishlist;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistRepository extends JpaRepository<Wishlist, Integer> {

    boolean existsByUserIdAndCardId(Integer userId, Integer cardId);

    List<Wishlist> findByUserId(Integer userId);

    List<Wishlist> findByCardId(Integer cardId);

    List<Wishlist> findByCardIdIn(Collection<Integer> cardIds);

    long countByCardId(Integer cardId);

    void deleteByUserIdAndCardId(Integer userId, Integer cardId);
}
