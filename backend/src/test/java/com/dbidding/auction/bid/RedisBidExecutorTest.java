package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.BidStatus;
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
    private RedisScript<String> bidStubScript;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);
    private RedisBidExecutor redisBidExecutor;

    @BeforeEach
    void setUp() {
        redisBidExecutor = new RedisBidExecutor(redisTemplate, bidStubScript, clock);
    }

    @Test
    void 경매_컨텍스트_키와_요청_인자를_그대로_EVAL에_전달한다() {
        when(redisTemplate.execute(eq(bidStubScript), eq(List.of("auction:1")), eq("2"), eq("43000")))
                .thenReturn("auction:1:2:43000");

        redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key"));

        verify(redisTemplate).execute(bidStubScript, List.of("auction:1"), "2", "43000");
    }

    @Test
    void EVAL_결과와_무관하게_요청값_기반의_placeholder_결과를_반환한다() {
        when(redisTemplate.execute(eq(bidStubScript), eq(List.of("auction:1")), eq("2"), eq("43000")))
                .thenReturn("auction:1:2:43000");

        var response = redisBidExecutor.execute(new BidCommand(2, 1, 43_000L, "bid-key"));

        assertThat(response.bid().id()).isEqualTo(0L);
        assertThat(response.bid().amount()).isEqualTo(43_000L);
        assertThat(response.bid().status()).isEqualTo(BidStatus.LEADING);
        assertThat(response.bid().createdAt()).isEqualTo(clock.instant());
        assertThat(response.auction().id()).isEqualTo(1);
        assertThat(response.auction().currentPrice()).isEqualTo(43_000L);
        assertThat(response.auction().minimumBid()).isEqualTo(43_000L);
        assertThat(response.auction().bidCount()).isZero();
        assertThat(response.auction().endsAt()).isEqualTo(clock.instant());
        assertThat(response.wallet().availableBalance()).isZero();
        assertThat(response.wallet().frozenBalance()).isZero();
    }
}
