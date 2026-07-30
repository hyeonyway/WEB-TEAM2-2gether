package com.dbidding.statistic.port;

import java.util.Collection;
import java.util.Map;

public interface StatisticCardPort {
    boolean exists(Integer itemId);

    Map<Integer, CardSnapshot> getCards(Collection<Integer> cardIds);

    record CardSnapshot(
            Integer cardId,
            String name,
            String theme,
            String imageUrl
    ) {
    }
}
