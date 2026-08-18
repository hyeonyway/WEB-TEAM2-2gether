package com.dbidding.order.dto;

import com.dbidding.order.domain.Order;
import com.dbidding.order.domain.OrderStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record OrderResponse(
        Integer id,
        @JsonProperty("auction_id") Integer auctionId,
        @JsonProperty("card_name") String cardName,
        long price,
        OrderStatus status,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("stream_id") String streamId
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getAuctionId(),
                order.getCardName(),
                order.getPrice(),
                order.getStatus(),
                order.getCreatedAt(),
                null
        );
    }
}
