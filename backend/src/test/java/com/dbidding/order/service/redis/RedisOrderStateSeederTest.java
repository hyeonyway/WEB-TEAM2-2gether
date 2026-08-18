package com.dbidding.order.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.dbidding.order.domain.Order;
import com.dbidding.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisOrderStateSeederTest {
    private final OrderRepository orderRepository = Mockito.mock(OrderRepository.class);
    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final RedisProjectionCatchUpVerifier catchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
    @SuppressWarnings("unchecked")
    private final RedisScript<Long> orderStateSeedScript = Mockito.mock(RedisScript.class);
    private final RedisOrderStateSeeder seeder = new RedisOrderStateSeeder(
            orderRepository, redisTemplate, catchUpVerifier, new RedisStateSingleFlight(), orderStateSeedScript
    );

    @Test
    void seedIfAbsent_orderId는_주문의_경매_단위로_catch_up을_확인한다() {
        Order order = order(100, 10);
        when(redisTemplate.hasKey("order:state:by-order-id:100")).thenReturn(false);
        when(orderRepository.findById(100)).thenReturn(java.util.Optional.of(order));
        when(catchUpVerifier.isCaughtUpForAuctionFresh(10)).thenReturn(false);

        assertThatThrownBy(() -> seeder.seedIfAbsent(100)).isInstanceOf(RuntimeException.class);

        verify(catchUpVerifier).isCaughtUpForAuctionFresh(10);
        verify(orderRepository).findById(100);
    }

    @Test
    void seedIfAbsent_Order는_그_주문의_경매_단위로_catch_up을_fresh하게_확인한다() {
        Order order = order(100, 10);
        when(redisTemplate.hasKey("order:state:by-order-id:100")).thenReturn(false);
        when(catchUpVerifier.isCaughtUpForAuctionFresh(10)).thenReturn(false);

        assertThatThrownBy(() -> seeder.seedIfAbsent(order)).isInstanceOf(RuntimeException.class);

        verify(catchUpVerifier).isCaughtUpForAuctionFresh(10);
    }

    @Test
    void seedAssumingCaughtUp은_catch_up을_다시_확인하지_않는다() {
        Order first = order(1, 10);
        Order second = order(2, 11);
        when(redisTemplate.hasKey("order:state:by-order-id:1")).thenReturn(false);
        when(redisTemplate.hasKey("order:state:by-order-id:2")).thenReturn(false);

        seeder.seedAssumingCaughtUp(first);
        seeder.seedAssumingCaughtUp(second);

        verify(catchUpVerifier, times(0)).isCaughtUpForAuction(any());
        verify(catchUpVerifier, times(0)).isCaughtUpForAuctionFresh(any());
    }

    @Test
    void seedAssumingCaughtUp은_이미_있으면_다시_시딩하지_않는다() {
        Order order = order(1, 10);
        when(redisTemplate.hasKey("order:state:by-order-id:1")).thenReturn(true);

        boolean seeded = seeder.seedAssumingCaughtUp(order);

        assertThat(seeded).isFalse();
        verify(redisTemplate, never()).execute(Mockito.eq(orderStateSeedScript), anyList(), Mockito.any());
    }

    private Order order(int id, int auctionId) {
        Order order = Mockito.mock(Order.class);
        when(order.getId()).thenReturn(id);
        when(order.getAuctionId()).thenReturn(auctionId);
        when(order.getBuyerId()).thenReturn(3);
        when(order.getSellerId()).thenReturn(4);
        when(order.getCardName()).thenReturn("리자몽");
        when(order.getPrice()).thenReturn(10_000L);
        when(order.getStatus()).thenReturn(com.dbidding.order.domain.OrderStatus.PENDING_CONFIRM);
        when(order.getCreatedAt()).thenReturn(java.time.Instant.parse("2026-08-14T00:00:00Z"));
        return order;
    }
}
