package com.dbidding.auction.stream;

import com.dbidding.global.redis.RedisIntegerValue;
import com.dbidding.order.domain.OrderStatus;
import com.dbidding.wallet.domain.PointTransactionType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Redis Lua가 승인한 주문 상태·지갑 상태의 결합 snapshot이다. */
public record OrderStateChangedStreamEvent(
        String streamId, UUID eventId, String eventType, Integer orderId, Integer auctionId, Long orderVersion,
        Integer actorId, Integer buyerId, Integer sellerId, OrderStatus status, Integer walletUserId,
        Long walletVersion, Long availableBalance, Long frozenBalance, PointTransactionType transactionType,
        Long transactionAmount, String idempotencyKey, Instant occurredAt
) implements AuctionWalletTimelineEvent {
    public static OrderStateChangedStreamEvent from(String streamId, Map<String, String> values) {
        try {
            if (!"1".equals(values.get("schemaVersion"))) throw new InvalidBidStreamEventException("지원하지 않는 주문 상태 Stream 이벤트입니다.");
            return new OrderStateChangedStreamEvent(streamId, UUID.fromString(required(values, "eventId")), required(values, "eventType"),
                    Integer.valueOf(required(values, "orderId")), Integer.valueOf(required(values, "auctionId")), RedisIntegerValue.parseLongExact(required(values, "orderVersion")),
                    Integer.valueOf(required(values, "actorId")), Integer.valueOf(required(values, "buyerId")), Integer.valueOf(required(values, "sellerId")),
                    OrderStatus.valueOf(required(values, "status")), Integer.valueOf(required(values, "walletUserId")),
                    RedisIntegerValue.parseLongExact(required(values, "walletVersion")), RedisIntegerValue.parseLongExact(required(values, "availableBalance")),
                    RedisIntegerValue.parseLongExact(required(values, "frozenBalance")),
                    PointTransactionType.valueOf(required(values, "transactionType")), RedisIntegerValue.parseLongExact(required(values, "transactionAmount")),
                    required(values, "idempotencyKey"), Instant.parse(required(values, "occurredAt")));
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException("주문 상태 Stream 이벤트 형식이 올바르지 않습니다.", exception);
        }
    }
    @Override public String archiveEventType() { return eventType; }
    @Override public int schemaVersion() { return 1; }
    @Override public String archivePayload() { return "schemaVersion=1&eventId=" + eventId + "&eventType=" + eventType + "&orderId=" + orderId + "&auctionId=" + auctionId + "&orderVersion=" + orderVersion + "&status=" + status + "&walletUserId=" + walletUserId + "&walletVersion=" + walletVersion + "&occurredAt=" + occurredAt; }
    private static String required(Map<String, String> values, String field) { String value = values.get(field); if (value == null || value.isBlank()) throw new IllegalArgumentException("missing " + field); return value; }
}
