package com.dbidding.auction.bid.dto;

import java.time.Instant;
import java.util.List;

public record RedisAuctionCreateCommand(
        Integer sellerId,
        Integer itemId,
        String cardName,
        String cardSetName,
        String cardPsaGrade,
        String cardLanguage,
        String cardThumbnailUrl,
        String auctionName,
        String description,
        String sellerMemo,
        String psaCertification,
        String selfGrade,
        boolean psaVerified,
        long startPrice,
        Long buyNowPrice,
        long deliveryFee,
        long bidPriceUnit,
        List<String> imagePaths,
        Instant closeTime,
        String idempotencyKey,
        String idempotencyRequestHash
) {
}
