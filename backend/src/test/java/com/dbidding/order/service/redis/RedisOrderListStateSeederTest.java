package com.dbidding.order.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.dbidding.order.domain.Order;
import com.dbidding.order.repository.OrderRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisOrderListStateSeederTest {
    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final RedisOrderStateSeeder orderStateSeeder = Mockito.mock(RedisOrderStateSeeder.class);
    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final RedisProjectionCatchUpVerifier catchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = Mockito.mock(ValueOperations.class);
    private final RedisOrderListStateSeeder seeder = new RedisOrderListStateSeeder(
            orderRepository, orderStateSeeder, redisTemplate, catchUpVerifier, new RedisStateSingleFlight()
    );

    @Test
    void 마커가_없으면_구매자의_모든_주문을_MySQL에서_시딩하고_마커를_기록한다() {
        Order order1 = Mockito.mock(Order.class);
        Order order2 = Mockito.mock(Order.class);
        when(redisTemplate.hasKey("order:state:seeded:buyer:7")).thenReturn(false);
        when(catchUpVerifier.isCaughtUp()).thenReturn(true);
        when(orderRepository.findByBuyerIdOrderByIdDesc(7)).thenReturn(List.of(order1, order2));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        seeder.seedIfRequired(7, true);

        verify(catchUpVerifier, times(1)).isCaughtUp();
        verify(orderStateSeeder).seedAssumingCaughtUp(order1);
        verify(orderStateSeeder).seedAssumingCaughtUp(order2);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.eq("order:state:seeded:buyer:7"), org.mockito.ArgumentMatchers.eq("1"),
                org.mockito.ArgumentMatchers.any(java.time.Duration.class));
    }

    @Test
    void 마커가_있으면_MySQL을_다시_조회하지_않는다() {
        when(redisTemplate.hasKey("order:state:seeded:seller:7")).thenReturn(true);

        seeder.seedIfRequired(7, false);

        verify(orderRepository, never()).findBySellerIdOrderByIdDesc(org.mockito.ArgumentMatchers.any());
        verify(catchUpVerifier, never()).isCaughtUp();
    }

    @Test
    void projection이_따라잡지_못했으면_복구가_필요하다는_예외를_던진다() {
        when(redisTemplate.hasKey("order:state:seeded:buyer:7")).thenReturn(false);
        when(catchUpVerifier.isCaughtUp()).thenReturn(false);

        assertThatThrownBy(() -> seeder.seedIfRequired(7, true)).isInstanceOf(RuntimeException.class);

        verify(orderRepository, times(0)).findByBuyerIdOrderByIdDesc(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void auctionId로_주문을_찾으면_그_주문만_시딩한다() {
        Order order = Mockito.mock(Order.class);
        when(orderRepository.findByAuctionId(10)).thenReturn(java.util.Optional.of(order));
        when(orderStateSeeder.seedIfAbsent(order)).thenReturn(true);

        assertThat(seeder.seedIfMissing(10)).isTrue();
    }

    @Test
    void auctionId로_주문을_못_찾으면_false를_반환한다() {
        when(orderRepository.findByAuctionId(10)).thenReturn(java.util.Optional.empty());

        assertThat(seeder.seedIfMissing(10)).isFalse();
    }
}
