package com.dbidding.notification.adapter;

import com.dbidding.notification.port.WishlistUserFinder;
import com.dbidding.wishlist.WishlistService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WishlistUserFinderAdapter implements WishlistUserFinder {

    private final WishlistService wishlistService;

    @Override
    public List<Integer> findUserIdsByCardId(Integer cardId) {
        return wishlistService.findUserIdsByCardId(cardId);
    }
}