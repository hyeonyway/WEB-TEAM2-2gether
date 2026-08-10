package com.dbidding.order.event;

public record OrderCompletedEvent(
        Integer orderId,
        Integer auctionId,
        Integer buyerId,
        Integer sellerId,
        String cardName
) {
}
