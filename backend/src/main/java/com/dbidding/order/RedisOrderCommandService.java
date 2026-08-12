package com.dbidding.order;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.order.dto.OrderResponse;
import com.dbidding.order.exception.InvalidOrderStatusException;
import com.dbidding.order.exception.OrderAccessDeniedException;
import com.dbidding.order.exception.OrderNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.dbidding.wallet.service.RedisWalletStateSeeder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
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
    private final Clock clock;

    public RedisOrderCommandService(
            StringRedisTemplate redisTemplate,
            @Qualifier("orderWalletTransitionScript") RedisScript<String> orderWalletTransitionScript,
            RedisOrderStateSeeder stateSeeder,
            RedisWalletStateSeeder walletStateSeeder,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.orderWalletTransitionScript = orderWalletTransitionScript;
        this.stateSeeder = stateSeeder;
        this.walletStateSeeder = walletStateSeeder;
        this.clock = clock;
    }

    public OrderResponse confirm(Integer orderId, Integer actorId) {
        return transition(orderId, actorId, OrderStatus.COMPLETED, "order.completed.v1", "confirm");
    }

    public OrderResponse cancel(Integer orderId, Integer actorId) {
        return transition(orderId, actorId, OrderStatus.CANCELLED, "order.cancelled.v1", "buyer-cancel");
    }

    public OrderResponse sellerCancel(Integer orderId, Integer actorId) {
        return transition(orderId, actorId, OrderStatus.CANCELLED, "order.cancelled.v1", "seller-cancel");
    }

    private OrderResponse transition(Integer orderId, Integer actorId, OrderStatus targetStatus, String eventType, String command) {
        Map<Object, Object> order = findOrderState(orderId);
        Integer auctionId = integer(order, "auctionId");
        Integer buyerId = integer(order, "buyerId");
        Integer sellerId = integer(order, "sellerId");
        if (targetStatus == OrderStatus.COMPLETED && !buyerId.equals(actorId)) throw new OrderAccessDeniedException();
        if (targetStatus == OrderStatus.CANCELLED && !buyerId.equals(actorId) && !sellerId.equals(actorId)) throw new OrderAccessDeniedException();
        Integer walletUserId = targetStatus == OrderStatus.COMPLETED ? sellerId : buyerId;
        walletStateSeeder.seedIfAbsent(walletUserId);
        String idempotencyKey = command + ':' + orderId;
        String requestHash = eventType + ':' + actorId;
        Instant now = clock.instant();
        String raw = redisTemplate.execute(orderWalletTransitionScript, List.of(
                        stateKey(auctionId), balanceKey(walletUserId), "order:idempotency:" + orderId + ':' + idempotencyKey, TIMELINE_STREAM
                ), actorId.toString(), targetStatus.name(), eventType, orderId.toString(), auctionId.toString(), idempotencyKey,
                requestHash, UUID.randomUUID().toString(), now.toString());
        String[] fields = raw.split("\\|", -1);
        if (!"ACCEPTED".equals(fields[0])) throw rejected(fields.length > 1 ? fields[1] : "STATE_MISSING");
        return new OrderResponse(orderId, auctionId, required(order, "cardName"), longValue(order, "price"),
                OrderStatus.valueOf(fields[2]), Instant.parse(required(order, "createdAt")), fields[1]);
    }

    private RuntimeException rejected(String reason) {
        return switch (reason) {
            case "ACCESS_DENIED" -> new OrderAccessDeniedException();
            case "INVALID_STATUS" -> new InvalidOrderStatusException();
            case "IDEMPOTENCY_CONFLICT" -> AuctionException.idempotencyConflict();
            case "STATE_MISSING" -> new OrderNotFoundException();
            default -> new IllegalStateException("Redis 주문 상태 전이에 실패했습니다: " + reason);
        };
    }

    private Map<Object, Object> findOrderState(Integer orderId) {
        // projection이 orderId를 넣은 뒤 주문 명령을 허용한다. 목록 조회의 pending 주문에는 명령을 노출하지 않는다.
        String auctionId = redisTemplate.opsForValue().get("order:state:by-order-id:" + orderId);
        if (auctionId == null || auctionId.isBlank()) {
            stateSeeder.seedIfAbsent(orderId);
            auctionId = redisTemplate.opsForValue().get("order:state:by-order-id:" + orderId);
        }
        if (auctionId == null || auctionId.isBlank()) throw new OrderNotFoundException();
        Map<Object, Object> order = redisTemplate.opsForHash().entries(stateKey(Integer.valueOf(auctionId)));
        if (order.isEmpty() || !String.valueOf(orderId).equals(required(order, "orderId"))) throw new OrderNotFoundException();
        return order;
    }

    private String required(Map<Object, Object> values, String field) { Object value = values.get(field); if (value == null || value.toString().isBlank()) throw new OrderNotFoundException(); return value.toString(); }
    private Integer integer(Map<Object, Object> values, String field) { return Integer.valueOf(required(values, field)); }
    private long longValue(Map<Object, Object> values, String field) { return Long.parseLong(required(values, field)); }
    private String stateKey(Integer auctionId) { return "order:state:" + auctionId; }
    private String balanceKey(Integer userId) { return "wallet:balance:" + userId; }
}
