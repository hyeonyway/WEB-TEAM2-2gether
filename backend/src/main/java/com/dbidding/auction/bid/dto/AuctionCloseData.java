package com.dbidding.auction.bid.dto;

public record AuctionCloseData(
        Integer cardId,
        String cardName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        Integer sellerId
) {
}
