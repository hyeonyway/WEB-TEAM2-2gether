package com.dbidding.order.service.redis;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.global.redis.RedisIntegerValue;
import com.dbidding.order.domain.OrderStatus;
import com.dbidding.order.dto.OrderResponse;
import com.dbidding.order.exception.InvalidOrderStatusException;
import com.dbidding.order.exception.OrderAccessDeniedException;
import com.dbidding.order.exception.OrderNotFoundException;
import com.dbidding.order.port.OrderEventPort;
import com.dbidding.wallet.domain.WalletAmountPolicy;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.service.redis.RedisWalletStateSeeder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

/** 주문이 유발하는 지갑 변경만 Redis 승인 경계에서 처리한다. */
@Service
@Profile("redis")
public class RedisOrderCommandService {
    private static final String TIMELINE_STREAM = "event:timeline";
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<String> orderWalletTransitionScript;
    private final RedisOrderStateSeeder stateSeeder;
    private final RedisWalletStateSeeder walletStateSeeder;
    private final RedisScript<String> orderStateReadScript;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final OrderEventPort orderEventPort;
    private final ApplicationEventPublisher eventPublisher;

    public RedisOrderCommandService(
            StringRedisTemplate redisTemplate,
            @Qualifier("orderWalletTransitionScript") RedisScript<String> orderWalletTransitionScript,
            RedisOrderStateSeeder stateSeeder,
            RedisWalletStateSeeder walletStateSeeder,
            @Qualifier("orderStateReadScript") RedisScript<String> orderStateReadScript,
            ObjectMapper objectMapper,
            Clock clock,
            OrderEventPort orderEventPort,
            ApplicationEventPublisher eventPublisher
    ) {
        this.redisTemplate = redisTemplate;
        this.orderWalletTransitionScript = orderWalletTransitionScript;
        this.stateSeeder = stateSeeder;
        this.walletStateSeeder = walletStateSeeder;
        this.orderStateReadScript = orderStateReadScript;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.orderEventPort = orderEventPort;
        this.eventPublisher = eventPublisher;
    }

    public OrderResponse confirm(Integer orderId, Integer actorId) {
        return transition(orderId, actorId, OrderStatus.COMPLETED, "order.completed.v1", "confirm");
    }

    public OrderResponse cancel(Integer orderId, Integer actorId) {
        return transition(orderId, actorId, OrderStatus.CANCELLED, "order.buyer-cancelled.v1", "buyer-cancel");
    }

    public OrderResponse sellerCancel(Integer orderId, Integer actorId) {
        return transition(orderId, actorId, OrderStatus.CANCELLED, "order.seller-cancelled.v1", "seller-cancel");
    }

    private OrderResponse transition(Integer orderId, Integer actorId, OrderStatus targetStatus, String eventType, String command) {
        Map<Object, Object> order = findOrderState(orderId);
        Integer auctionId = integer(order, "auctionId");
        Integer buyerId = integer(order, "buyerId");
        Integer sellerId = integer(order, "sellerId");
        if (targetStatus == OrderStatus.COMPLETED && !buyerId.equals(actorId)) throw new OrderAccessDeniedException();
        if ("buyer-cancel".equals(command) && !buyerId.equals(actorId)) throw new OrderAccessDeniedException();
        if ("seller-cancel".equals(command) && !sellerId.equals(actorId)) throw new OrderAccessDeniedException();
        Integer walletUserId = targetStatus == OrderStatus.COMPLETED ? sellerId : buyerId;
        walletStateSeeder.seedIfAbsent(walletUserId);
        String idempotencyKey = command + ':' + orderId;
        String requestHash = eventType + ':' + actorId;
        Instant now = clock.instant();
        String raw = redisTemplate.execute(orderWalletTransitionScript, List.of(
                        stateKey(auctionId), balanceKey(walletUserId), "order:idempotency:" + orderId + ':' + idempotencyKey, TIMELINE_STREAM,
                        "order:state:by-order-id:" + orderId
                ), actorId.toString(), targetStatus.name(), eventType, orderId.toString(), auctionId.toString(), idempotencyKey,
                requestHash, UUID.randomUUID().toString(), now.toString(),
                Long.toString(WalletAmountPolicy.MAX_BALANCE));
        String[] fields = raw.split("\\|", -1);
        if (!"ACCEPTED".equals(fields[0])) throw rejected(fields.length > 1 ? fields[1] : "STATE_MISSING");
        if (fields.length != 9) throw new IllegalStateException("Redis 주문 승인 응답이 올바르지 않습니다.");
        if (!Boolean.parseBoolean(fields[8])) {
            publishApprovedOrder(orderId, auctionId, actorId, buyerId, sellerId, required(order, "cardName"), targetStatus);
            long availableBalance = RedisIntegerValue.parseLongExact(fields[5]);
            long frozenBalance = RedisIntegerValue.parseLongExact(fields[6]);
            long walletVersion = RedisIntegerValue.parseLongExact(fields[4]);
            eventPublisher.publishEvent(new com.dbidding.wallet.sse.WalletBalanceChangedEvent(
                    Integer.valueOf(fields[7]),
                    new com.dbidding.wallet.dto.WalletBalanceResponse(
                            Math.addExact(availableBalance, frozenBalance), frozenBalance, availableBalance, walletVersion),
                    walletVersion,
                    now
            ));
        }
        return new OrderResponse(orderId, auctionId, required(order, "cardName"), longValue(order, "price"),
                OrderStatus.valueOf(fields[2]), Instant.parse(required(order, "createdAt")), fields[1]);
    }

    private void publishApprovedOrder(Integer orderId, Integer auctionId, Integer actorId, Integer buyerId,
                                      Integer sellerId, String cardName, OrderStatus status) {
        if (status == OrderStatus.COMPLETED) {
            orderEventPort.publishCompleted(new com.dbidding.order.event.OrderCompletedEvent(
                    orderId, auctionId, buyerId, sellerId, cardName));
            return;
        }
        orderEventPort.publishCancelled(new com.dbidding.order.event.OrderCancelledEvent(
                orderId, auctionId, buyerId, sellerId, cardName,
                actorId.equals(buyerId)
                        ? com.dbidding.order.event.OrderCancelledEvent.CancelledBy.BUYER
                        : com.dbidding.order.event.OrderCancelledEvent.CancelledBy.SELLER));
    }

    private RuntimeException rejected(String reason) {
        return switch (reason) {
            case "ACCESS_DENIED" -> new OrderAccessDeniedException();
            case "INVALID_STATUS" -> new InvalidOrderStatusException();
            case "IDEMPOTENCY_CONFLICT" -> AuctionException.idempotencyConflict();
            case "STATE_MISSING" -> new OrderNotFoundException();
            case "AMOUNT_LIMIT_EXCEEDED", "BALANCE_LIMIT_EXCEEDED" ->
                    new InvalidWalletAmountException("지갑 및 주문 금액이 허용 상한을 초과했습니다.");
            default -> new IllegalStateException("Redis 주문 상태 전이에 실패했습니다: " + reason);
        };
    }

    private Map<Object, Object> findOrderState(Integer orderId) {
        Map<Object, Object> order = readOrderState(orderId);
        if (order.isEmpty()) {
            stateSeeder.seedIfAbsent(orderId);
            order = readOrderState(orderId);
        }
        if (order.isEmpty()) throw new OrderNotFoundException();
        if (order.isEmpty() || !String.valueOf(orderId).equals(required(order, "orderId"))) throw new OrderNotFoundException();
        return order;
    }

    private Map<Object, Object> readOrderState(Integer orderId) {
        String raw = redisTemplate.execute(orderStateReadScript, List.of("order:state:by-order-id:" + orderId));
        if (raw == null || raw.isBlank()) return Map.of();
        try {
            return new java.util.HashMap<>(objectMapper.readValue(raw, new TypeReference<Map<String, String>>() { }));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Redis 주문 상태를 읽을 수 없습니다.", exception);
        }
    }

    private String required(Map<Object, Object> values, String field) { Object value = values.get(field); if (value == null || value.toString().isBlank()) throw new OrderNotFoundException(); return value.toString(); }
    private Integer integer(Map<Object, Object> values, String field) { return Integer.valueOf(required(values, field)); }
    private long longValue(Map<Object, Object> values, String field) { return RedisIntegerValue.parseLongExact(required(values, field)); }
    private String stateKey(Integer auctionId) { return "order:state:" + auctionId; }
    private String balanceKey(Integer userId) { return "wallet:balance:" + userId; }
}
