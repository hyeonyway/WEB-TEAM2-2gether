package com.dbidding.order.service.redis;

import com.dbidding.order.domain.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** MySQL 주문 projection 결과를 Redis 주문 현재 상태에 연결한다. */
@Component
@Profile("redis")
public class RedisOrderRealtimeStateProjection {
    private final StringRedisTemplate redisTemplate;

    public RedisOrderRealtimeStateProjection(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void markProjectedAfterCommit(Integer auctionId, Integer orderId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    markProjected(auctionId, orderId);
                }
            });
            return;
        }
        markProjected(auctionId, orderId);
    }

    /** Scheduler 마감으로 새로 생성된 주문을 Redis 목록에도 반영한다. */
    public void markCreatedOrderAfterCommit(Order order, String streamId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    markCreatedOrder(order, streamId);
                }
            });
            return;
        }
        markCreatedOrder(order, streamId);
    }

    public void markProjectionError(Integer auctionId) {
        redisTemplate.opsForHash().put("order:state:" + auctionId, "projectionStatus", "PROJECTION_ERROR");
    }

    public void markProjectedStatusAfterCommit(Integer auctionId, Integer orderId, String status) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { markProjectedStatus(auctionId, orderId, status); }
            });
            return;
        }
        markProjectedStatus(auctionId, orderId, status);
    }

    private void markProjected(Integer auctionId, Integer orderId) {
        redisTemplate.opsForHash().putAll("order:state:" + auctionId, java.util.Map.of(
                "orderId", String.valueOf(orderId), "projectionStatus", "PROJECTED"
        ));
        redisTemplate.opsForValue().set("order:state:by-order-id:" + orderId, String.valueOf(auctionId));
    }

    private void markCreatedOrder(Order order, String streamId) {
        String auctionId = String.valueOf(order.getAuctionId());
        redisTemplate.opsForHash().putAll("order:state:" + auctionId, java.util.Map.of(
                "orderId", String.valueOf(order.getId()), "auctionId", auctionId,
                "buyerId", String.valueOf(order.getBuyerId()), "sellerId", String.valueOf(order.getSellerId()),
                "cardName", order.getCardName(), "price", String.valueOf(order.getPrice()),
                "status", order.getStatus().name(), "streamId", streamId,
                "createdAt", order.getCreatedAt().toString(), "projectionStatus", "PROJECTED"
        ));
        redisTemplate.opsForValue().set("order:state:by-order-id:" + order.getId(), auctionId);
        redisTemplate.opsForSet().add("order:state:buyer:" + order.getBuyerId(), auctionId);
        redisTemplate.opsForSet().add("order:state:seller:" + order.getSellerId(), auctionId);
    }

    private void markProjectedStatus(Integer auctionId, Integer orderId, String status) {
        redisTemplate.opsForHash().putAll("order:state:" + auctionId, java.util.Map.of(
                "orderId", String.valueOf(orderId), "status", status, "projectionStatus", "PROJECTED"
        ));
    }
}
