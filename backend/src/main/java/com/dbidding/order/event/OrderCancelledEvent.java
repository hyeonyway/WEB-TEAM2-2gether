package com.dbidding.order.event;

public record OrderCancelledEvent(
        Integer orderId,
        Integer auctionId,
        Integer buyerId,
        Integer sellerId
) {
}
