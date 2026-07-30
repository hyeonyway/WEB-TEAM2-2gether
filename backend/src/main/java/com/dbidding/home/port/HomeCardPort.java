package com.dbidding.home.port;

import java.util.Collection;
import java.util.Map;

public interface HomeCardPort {
    Map<Integer, CardSnapshot> getCards(Collection<Integer> cardIds);

    record CardSnapshot(
            Integer cardId,
            String name,
            String theme,
            String imageUrl
    ) {
    }
}
