package com.dbidding.order;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 주문 목록 조회 시 참여 인덱스가 비어있거나 TTL로 만료됐으면 MySQL 주문 이력에서 온디맨드로 다시 채운다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisOrderListStateSeeder {
    private final OrderRepository orderRepository;
    private final RedisOrderStateSeeder orderStateSeeder;
    private final StringRedisTemplate redisTemplate;
    private final RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    private final RedisStateSingleFlight singleFlight;

    public void seedIfRequired(Integer userId, boolean asBuyer) {
        String markerKey = "order:state:seeded:" + (asBuyer ? "buyer:" : "seller:") + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(markerKey))) return;
        singleFlight.execute(markerKey, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(markerKey))) return false;
            if (!projectionCatchUpVerifier.isCaughtUp()) throw AuctionException.stateRecoveryRequired();
            var orders = asBuyer
                    ? orderRepository.findByBuyerIdOrderByIdDesc(userId)
                    : orderRepository.findBySellerIdOrderByIdDesc(userId);
            orders.forEach(orderStateSeeder::seedIfAbsent);
            redisTemplate.opsForValue().set(markerKey, "1", Duration.ofSeconds(3600 + Math.floorMod(userId.longValue(), 18001)));
            return true;
        });
    }
}
