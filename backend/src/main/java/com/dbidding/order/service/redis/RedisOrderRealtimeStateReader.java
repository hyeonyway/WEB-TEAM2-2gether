package com.dbidding.order.service.redis;

import com.dbidding.order.domain.OrderStatus;
import com.dbidding.order.dto.OrderResponse;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Redis 승인 직후부터 MySQL projection 완료 뒤까지 주문 최신 상태를 읽는다. */
@Component
@Profile("redis")
public class RedisOrderRealtimeStateReader {
    private final StringRedisTemplate redisTemplate;
    private final RedisOrderListStateSeeder listStateSeeder;

    public RedisOrderRealtimeStateReader(StringRedisTemplate redisTemplate, RedisOrderListStateSeeder listStateSeeder) {
        this.redisTemplate = redisTemplate;
        this.listStateSeeder = listStateSeeder;
    }

    public List<OrderResponse> findForBuyer(Integer buyerId) {
        listStateSeeder.seedIfRequired(buyerId, true);
        return find("order:state:buyer:" + buyerId);
    }

    public List<OrderResponse> findForSeller(Integer sellerId) {
        listStateSeeder.seedIfRequired(sellerId, false);
        return find("order:state:seller:" + sellerId);
    }

    private List<OrderResponse> find(String indexKey) {
        var auctionIds = redisTemplate.opsForSet().members(indexKey);
        if (auctionIds == null || auctionIds.isEmpty()) return List.of();
        return auctionIds.stream().map(this::read).filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparing(OrderResponse::createdAt).reversed()).toList();
    }

    private OrderResponse read(String auctionId) {
        Map<Object, Object> fields = redisTemplate.opsForHash().entries("order:state:" + auctionId);
        if (fields.isEmpty()) {
            if (!listStateSeeder.seedIfMissing(Integer.valueOf(auctionId))) return null;
            fields = redisTemplate.opsForHash().entries("order:state:" + auctionId);
            if (fields.isEmpty()) return null;
        }
        try {
            return new OrderResponse(nullableInteger(fields.get("orderId")), Integer.valueOf(required(fields, "auctionId")), required(fields, "cardName"),
                    Long.parseLong(required(fields, "price")), OrderStatus.valueOf(required(fields, "status")),
                    Instant.parse(required(fields, "createdAt")), nullableString(fields.get("streamId")));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String required(Map<Object, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null || value.toString().isBlank()) throw new IllegalArgumentException("missing " + name);
        return value.toString();
    }

    private String nullableString(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private Integer nullableInteger(Object value) {
        return value == null || value.toString().isBlank() ? null : Integer.valueOf(value.toString());
    }
}
