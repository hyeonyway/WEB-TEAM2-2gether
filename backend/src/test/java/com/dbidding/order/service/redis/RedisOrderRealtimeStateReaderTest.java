package com.dbidding.order.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void MySQL에서_시딩된_주문처럼_streamId가_없어도_목록에서_제외하지_않는다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("order:state:seller:9")).thenReturn(Set.of("10"));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("order:state:10")).thenReturn(Map.of(
                "orderId", "100", "auctionId", "10", "cardName", "리자몽", "price", "50000",
                "status", "PENDING_CONFIRM", "createdAt", "2026-08-13T00:00:00Z"
        ));

        var result = reader.findForSeller(9);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().streamId()).isNull();
    }

    @Test
    void 목록_marker는_살아있어도_개별_order_state가_먼저_만료됐으면_그_주문만_다시_시딩한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("order:state:buyer:7")).thenReturn(Set.of("10"));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("order:state:10"))
                .thenReturn(Map.of())
                .thenReturn(Map.of(
                        "orderId", "100", "auctionId", "10", "cardName", "리자몽", "price", "50000",
                        "status", "COMPLETED", "createdAt", "2026-08-13T00:00:00Z", "streamId", "1-0"
                ));
        when(listStateSeeder.seedIfMissing(10)).thenReturn(true);

        var result = reader.findForBuyer(7);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(100);
        verify(listStateSeeder).seedIfMissing(10);
    }

    @Test
    void 재시딩해도_찾을_수_없으면_목록에서_제외한다() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("order:state:buyer:7")).thenReturn(Set.of("10"));
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("order:state:10")).thenReturn(Map.of());
        when(listStateSeeder.seedIfMissing(10)).thenReturn(false);

        assertThat(reader.findForBuyer(7)).isEmpty();
    }
}
