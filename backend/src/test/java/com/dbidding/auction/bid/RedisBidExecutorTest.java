package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class RedisBidExecutorTest {
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisScript<String> bidAcceptScript;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);
    private RedisBidExecutor redisBidExecutor;

    @BeforeEach
    void setUp() {
        redisBidExecutor = new RedisBidExecutor(redisTemplate, bidAcceptScript, clock);
    }

    @Test
    void Lua가_승인한_입찰의_eventId와_실시간_상태를_응답한다() {
        List<String> keys = List.of(
                "auction:state:1", "wallet:balance:2", "wallet:hold:1:2",
                "auction:bid:idempotency:1:2:bid-key", "event:timeline"
        );
        when(redisTemplate.execute(eq(bidAcceptScript), eq(keys),
                eq("2"), eq("43000"), eq("bid-key"), anyString(), eq("1786320000000"), eq("2026-08-10T00:00:00Z")))
                .thenReturn("ACCEPTED|1700000000000-0|43000|7|3|57000|43000|1|46000|2026-08-10T01:00:00Z|LEADING|");

        var response = redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key"));

        assertThat(response.result().bid().id()).isNull();
        assertThat(response.result().bid().eventId()).isEqualTo("1700000000000-0");
        assertThat(response.result().bid().amount()).isEqualTo(43_000L);
        assertThat(response.result().auction().currentPrice()).isEqualTo(43_000L);
        assertThat(response.result().auction().minimumBid()).isEqualTo(46_000L);
        assertThat(response.result().auction().bidCount()).isEqualTo(3);
        assertThat(response.result().wallet().availableBalance()).isEqualTo(57_000L);
        assertThat(response.result().wallet().frozenBalance()).isEqualTo(43_000L);
        assertThat(response.eventData()).isNull();
    }
}
