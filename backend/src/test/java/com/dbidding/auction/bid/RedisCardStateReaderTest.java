package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.card.domain.CardSet;
import com.dbidding.card.repository.CardMetadataRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisCardStateReaderTest {
    @SuppressWarnings("unchecked")
    private final RedisScript<Long> cardCacheSeedScript = Mockito.mock(RedisScript.class);

    @Test
    void 카드_snapshot을_Redis_상태에서_읽는다() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = Mockito.mock(HashOperations.class);
        Mockito.when(redisTemplate.opsForHash()).thenReturn(hashes);
        CardMetadataRepository cardRepository = Mockito.mock(CardMetadataRepository.class);
        Mockito.when(hashes.entries("card:cache:10")).thenReturn(Map.of(
                "name", "리자몽", "setName", "base", "psaGrade", "10", "language", "JP", "thumbnailUrl", "/cards/10.png"
        ));

        var snapshot = new RedisCardStateReader(redisTemplate, cardRepository, cardCacheSeedScript, 86_400, 3_600)
                .getCardSnapshot(10);

        assertThat(snapshot.cardId()).isEqualTo(10);
        assertThat(snapshot.name()).isEqualTo("리자몽");
        assertThat(snapshot.thumbnailUrl()).isEqualTo("/cards/10.png");
        verify(cardRepository, never()).findById(10);
    }

    @Test
    void cache_miss면_MySQL_snapshot을_적재하고_Lua_스크립트로_원자적으로_반환한다() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = Mockito.mock(HashOperations.class);
        CardMetadataRepository cardRepository = Mockito.mock(CardMetadataRepository.class);
        CardSet cardSet = Mockito.mock(CardSet.class);
        CardMetadata card = Mockito.mock(CardMetadata.class);
        Mockito.when(card.getId()).thenReturn(10);
        Mockito.when(card.getName()).thenReturn("리자몽");
        Mockito.when(card.getPsaGrade()).thenReturn("10");
        Mockito.when(card.getLanguage()).thenReturn("JP");
        Mockito.when(card.getImagePath()).thenReturn("/cards/10.png");
        Mockito.when(card.getCardSet()).thenReturn(cardSet);
        Mockito.when(cardSet.getName()).thenReturn("base");
        Mockito.when(redisTemplate.opsForHash()).thenReturn(hashes);
        Mockito.when(hashes.entries("card:cache:10")).thenReturn(Map.of());
        Mockito.when(cardRepository.findById(10)).thenReturn(Optional.of(card));
        Mockito.when(redisTemplate.execute(eq(cardCacheSeedScript), anyList(), any(Object[].class))).thenReturn(1L);

        var snapshot = new RedisCardStateReader(redisTemplate, cardRepository, cardCacheSeedScript, 86_400, 3_600)
                .getCardSnapshot(10);

        assertThat(snapshot.name()).isEqualTo("리자몽");
        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(redisTemplate).execute(eq(cardCacheSeedScript), keys.capture(), args.capture());
        assertThat(keys.getValue()).containsExactly("card:cache:10");
        assertThat(args.getValue()).containsExactly("리자몽", "base", "10", "JP", "/cards/10.png", "86410");
    }

    @Test
    void 카드_ID별_결정적_jitter로_cache_만료를_분산한다() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        CardMetadataRepository cardRepository = Mockito.mock(CardMetadataRepository.class);
        RedisCardStateReader reader =
                new RedisCardStateReader(redisTemplate, cardRepository, cardCacheSeedScript, 86_400, 3_600);

        assertThat(reader.ttlFor(10)).isEqualTo(86_410);
        assertThat(reader.ttlFor(11)).isEqualTo(86_411);
    }

    @Test
    void 여러_카드_snapshot은_cache_hit을_우선_사용한다() {
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        HashOperations<String, Object, Object> hashes = Mockito.mock(HashOperations.class);
        CardMetadataRepository cardRepository = Mockito.mock(CardMetadataRepository.class);
        Mockito.when(redisTemplate.opsForHash()).thenReturn(hashes);
        Mockito.when(hashes.entries("card:cache:10")).thenReturn(Map.of(
                "name", "리자몽", "setName", "base", "psaGrade", "10", "language", "JP", "thumbnailUrl", "/cards/10.png"
        ));

        var snapshots = new RedisCardStateReader(redisTemplate, cardRepository, cardCacheSeedScript, 86_400, 3_600)
                .getCardSnapshots(List.of(10));

        assertThat(snapshots.get(10).setName()).isEqualTo("base");
        verify(cardRepository, never()).findAllById(List.of(10));
    }
}
