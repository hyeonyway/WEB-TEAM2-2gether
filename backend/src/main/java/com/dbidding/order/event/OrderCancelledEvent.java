package com.dbidding.order.event;

public record OrderCancelledEvent(
        Integer orderId,
        Integer auctionId,
        Integer buyerId,
        Integer sellerId,
        String cardName,
        CancelledBy cancelledBy
) {
    public enum CancelledBy {BUYER, SELLER}
}
