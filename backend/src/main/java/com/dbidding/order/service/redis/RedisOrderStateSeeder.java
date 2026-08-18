package com.dbidding.order.service.redis;

import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.dbidding.order.domain.Order;
import com.dbidding.order.repository.OrderRepository;
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
            return orderRepository.findById(orderId).map(order -> {
                if (!projectionCatchUpVerifier.isCaughtUpForAuctionFresh(order.getAuctionId())) throw AuctionException.stateRecoveryRequired();
                return seed(order);
            }).orElse(false);
        });
    }

    /** 목록 조회 등으로 Order를 이미 읽어온 경우, 같은 주문을 다시 조회하지 않고 시딩만 한다. */
    boolean seedIfAbsent(Order order) {
        String indexKey = "order:state:by-order-id:" + order.getId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(indexKey))) return false;
        return singleFlight.execute(indexKey, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(indexKey))) return false;
            if (!projectionCatchUpVerifier.isCaughtUpForAuctionFresh(order.getAuctionId())) throw AuctionException.stateRecoveryRequired();
            return seed(order);
        });
    }

    /**
     * 호출부(RedisOrderListStateSeeder.seedIfRequired)가 여러 주문을 시딩하는 루프에 들어가기 전
     * 이미 isCaughtUp()을 한 번 확인한 경우 사용한다. 주문마다 같은 전역 상태를 다시 물어보는
     * 중복 쿼리(N+1)를 없애기 위한 경로이므로, 바깥에서 catch-up을 보장하지 않는 곳에서는 쓰면 안 된다.
     */
    boolean seedAssumingCaughtUp(Order order) {
        String indexKey = "order:state:by-order-id:" + order.getId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(indexKey))) return false;
        return singleFlight.execute(indexKey, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(indexKey))) return false;
            return seed(order);
        });
    }

    private boolean seed(Order order) {
        String indexKey = "order:state:by-order-id:" + order.getId();
        return Long.valueOf(1L).equals(redisTemplate.execute(orderStateSeedScript,
                List.of("order:state:" + order.getAuctionId(), indexKey, "order:state:buyer:" + order.getBuyerId(), "order:state:seller:" + order.getSellerId()),
                String.valueOf(order.getId()), String.valueOf(order.getAuctionId()), String.valueOf(order.getBuyerId()),
                String.valueOf(order.getSellerId()), order.getCardName(), String.valueOf(order.getPrice()), order.getStatus().name(), order.getCreatedAt().toString()
        ));
    }
}
