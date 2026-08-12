package com.dbidding.order;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** MySQL에 이미 projection된 주문을 Redis 주문 명령 직전에만 조건부 시딩한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
class RedisOrderStateSeeder {
    private final OrderRepository orderRepository;
    private final StringRedisTemplate redisTemplate;
    private final RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    private final RedisStateSingleFlight singleFlight;
    @Qualifier("orderStateSeedScript") private final RedisScript<Long> orderStateSeedScript;

    boolean seedIfAbsent(Integer orderId) {
        String indexKey = "order:state:by-order-id:" + orderId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(indexKey))) return false;
        return singleFlight.execute(indexKey, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(indexKey))) return false;
            if (!projectionCatchUpVerifier.isCaughtUp()) throw AuctionException.stateRecoveryRequired();
            return orderRepository.findById(orderId).map(order -> Long.valueOf(1L).equals(redisTemplate.execute(orderStateSeedScript,
                    List.of("order:state:" + order.getAuctionId(), indexKey, "order:state:buyer:" + order.getBuyerId(), "order:state:seller:" + order.getSellerId()),
                    String.valueOf(order.getId()), String.valueOf(order.getAuctionId()), String.valueOf(order.getBuyerId()),
                    String.valueOf(order.getSellerId()), order.getCardName(), String.valueOf(order.getPrice()), order.getStatus().name(), order.getCreatedAt().toString()
            ))).orElse(false);
        });
    }
}
