package com.dbidding.order.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.order.RedisOrderListStateSeeder;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.InOrder;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisOrderRealtimeStateReaderTest {
    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    private final RedisOrderListStateSeeder listStateSeeder = Mockito.mock(RedisOrderListStateSeeder.class);
    @SuppressWarnings("unchecked")
    private final SetOperations<String, String> setOperations = Mockito.mock(SetOperations.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = Mockito.mock(HashOperations.class);
    private final RedisOrderRealtimeStateReader reader = new RedisOrderRealtimeStateReader(redisTemplate, listStateSeeder);

    @Test
    void 구매목록_조회_전에_온디맨드_시딩을_먼저_시도한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("order:state:buyer:7")).thenReturn(Set.of());

        reader.findForBuyer(7);

        InOrder order = org.mockito.Mockito.inOrder(listStateSeeder, setOperations);
        order.verify(listStateSeeder).seedIfRequired(7, true);
        order.verify(setOperations).members("order:state:buyer:7");
    }

    @Test
    void 시딩_이후_인덱스가_비어있으면_빈_목록을_반환한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("order:state:buyer:7")).thenReturn(Set.of());

        assertThat(reader.findForBuyer(7)).isEmpty();
    }

    @Test
    void 시딩_이후_존재하는_주문_state를_읽어_반환한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("order:state:seller:9")).thenReturn(Set.of("10"));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("order:state:10")).thenReturn(Map.of(
                "orderId", "100", "auctionId", "10", "cardName", "리자몽", "price", "50000",
                "status", "PENDING_CONFIRM", "createdAt", "2026-08-13T00:00:00Z", "streamId", "1-0"
        ));

        var result = reader.findForSeller(9);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(100);
        verify(listStateSeeder).seedIfRequired(9, false);
    }
}
