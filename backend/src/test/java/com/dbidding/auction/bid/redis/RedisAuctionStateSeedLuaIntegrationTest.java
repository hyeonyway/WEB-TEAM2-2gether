package com.dbidding.auction.bid.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuctionStateSeedLuaIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> script;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/auction-state-seed.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 기존_Redis_경매_state는_MySQL_seed로_덮어쓰지_않는다() {
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");
        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "2", "status", "OPEN", "currentPrice", "100", "0", "0", "0", "100", "0", "500")).isEqualTo(1L);
        assertThat(redisTemplate.execute(script, keys,
                "2000", "1", "2", "status", "ENDING", "currentPrice", "200", "0", "0", "0", "200", "0", "600")).isZero();

        assertThat(redisTemplate.opsForHash().get("auction:state:1", "currentPrice")).isEqualTo("100");
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "1")).isEqualTo(1000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(100D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(500D);
    }

    @Test
    void state_생성과_함께_사용자_입찰상태와_최근_입찰을_기록한다() {
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");

        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "3", "status", "OPEN", "estimatedCloseTime", "1970-01-01T00:00:01Z", "estimatedCloseTimeEpochMillis", "1000",
                "2", "10", "OUTBID", "40000", "20", "LEADING", "43000",
                "2", "101", "10", "40000", "101", "2026-08-13T00:00:00Z", "102", "20", "43000", "102", "2026-08-13T00:01:00Z",
                "2", "43000", "750", "900"
        )).isEqualTo(1L);

        assertThat(redisTemplate.opsForHash().entries("auction:bidder:1:10"))
                .containsEntry("status", "OUTBID").containsEntry("amount", "40000");
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:10")).containsExactly("1");
        assertThat(redisTemplate.opsForStream().size("auction:recent-bids:1")).isEqualTo(2L);
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1")).isEqualTo(-299000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "1")).isEqualTo(2D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(43000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "1")).isEqualTo(750D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(900D);
    }
}
