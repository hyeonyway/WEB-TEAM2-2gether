package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisCardStateReaderTest {
    @Test
    void 카드_snapshot을_Redis_상태에서_읽는다() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = Mockito.mock(HashOperations.class);
        Mockito.when(redisTemplate.opsForHash()).thenReturn(hashes);
        Mockito.when(hashes.entries("card:state:10")).thenReturn(Map.of(
                "name", "리자몽", "setName", "base", "psaGrade", "10", "language", "JP", "thumbnailUrl", "/cards/10.png"
        ));

        var snapshot = new RedisCardStateReader(redisTemplate).getCardSnapshot(10);

        assertThat(snapshot.cardId()).isEqualTo(10);
        assertThat(snapshot.name()).isEqualTo("리자몽");
        assertThat(snapshot.thumbnailUrl()).isEqualTo("/cards/10.png");
    }
}
