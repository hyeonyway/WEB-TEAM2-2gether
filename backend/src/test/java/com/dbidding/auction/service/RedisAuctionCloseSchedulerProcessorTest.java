package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.RedisScript;

class RedisAuctionCloseSchedulerProcessorTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    @SuppressWarnings("unchecked")
    private final RedisScript<Long> auctionCloseRequestScript = mock(RedisScript.class);
    private final RedisAuctionCloseSchedulerProcessor processor = new RedisAuctionCloseSchedulerProcessor(
            redisTemplate, auctionCloseRequestScript
    );

    @Test
    void 종료된_경매_context를_갱신하고_종료_요청_event를_발행한다() {
        Instant now = Instant.parse("2026-08-12T01:00:00Z");
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore("auction:active:by-close-time", 0, now.toEpochMilli(), 0, 100))
                .thenReturn(new java.util.LinkedHashSet<>(List.of("11", "12")));
        when(redisTemplate.execute(eq(auctionCloseRequestScript), org.mockito.ArgumentMatchers.anyList(), eq("11"), eq(now.toString()), eq("1786496400000")))
                .thenReturn(1L);
        when(redisTemplate.execute(eq(auctionCloseRequestScript), org.mockito.ArgumentMatchers.anyList(), eq("12"), eq(now.toString()), eq("1786496400000")))
                .thenReturn(0L);

        assertThat(processor.processDueAuctions(now, 100)).containsExactly(11);
        verify(redisTemplate).execute(eq(auctionCloseRequestScript),
                eq(List.of("auction:state:11", "event:timeline")),
                eq("11"), eq(now.toString()), eq("1786496400000"));
    }
}
