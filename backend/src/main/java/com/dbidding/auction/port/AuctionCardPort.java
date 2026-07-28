package com.dbidding.auction.port;

import java.util.Collection;
import java.util.Map;

public interface AuctionCardPort {
    CardSnapshot getCardSnapshot(Integer itemId);

    Map<Integer, CardSnapshot> getCardSnapshots(Collection<Integer> itemIds);

    record CardSnapshot(
            Integer itemId,
            String name,
            String setName,
            String psaGrade,
            String language,
            String thumbnailUrl
    ) {
    }
}
