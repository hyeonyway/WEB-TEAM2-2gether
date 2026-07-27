package com.dbidding.auction.port;

public interface AuctionCardPort {
    CardSnapshot getCardSnapshot(Integer itemId);

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
