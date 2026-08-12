package com.dbidding.order.realtime;

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

    public void markProjectionError(Integer auctionId) {
        redisTemplate.opsForHash().put("order:state:" + auctionId, "projectionStatus", "PROJECTION_ERROR");
    }

    private void markProjected(Integer auctionId, Integer orderId) {
        redisTemplate.opsForHash().putAll("order:state:" + auctionId, java.util.Map.of(
                "orderId", String.valueOf(orderId), "projectionStatus", "PROJECTED"
        ));
    }
}
