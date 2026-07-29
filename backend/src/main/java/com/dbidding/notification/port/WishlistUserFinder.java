package com.dbidding.notification.port;

import java.util.List;

public interface WishlistUserFinder {

    List<Integer> findUserIdsByCardId(Integer cardId);
}