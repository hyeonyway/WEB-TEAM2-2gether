package com.dbidding.auction.adapter.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisCardAuctionAdapterTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisScript<Long> cardActiveAuctionCountScript;

    @Test
    void Redis_활성_경매_인덱스에서_카드별_진행_경매_수를_읽는다() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);
        RedisCardAuctionAdapter adapter = new RedisCardAuctionAdapter(redisTemplate, cardActiveAuctionCountScript, clock);
        when(redisTemplate.execute(eq(cardActiveAuctionCountScript), eq(List.of("auction:active:by-close-time")),
                eq("198"), eq("1786579200000"))).thenReturn(3L);

        assertThat(adapter.countActiveAuctions(198)).isEqualTo(3);

        verify(redisTemplate).execute(eq(cardActiveAuctionCountScript), eq(List.of("auction:active:by-close-time")),
                eq("198"), eq("1786579200000"));
    }

    @Test
    void Redis에_해당_카드의_진행_경매가_없으면_0을_반환한다() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-13T00:00:00Z"), ZoneOffset.UTC);
        RedisCardAuctionAdapter adapter = new RedisCardAuctionAdapter(redisTemplate, cardActiveAuctionCountScript, clock);
        when(redisTemplate.execute(eq(cardActiveAuctionCountScript), eq(List.of("auction:active:by-close-time")),
                eq("198"), eq("1786579200000"))).thenReturn(0L);

        assertThat(adapter.countActiveAuctions(198)).isZero();
    }
}
