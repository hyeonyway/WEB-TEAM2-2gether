package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
class AuctionCloseRequestLuaIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<String> script;

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
        script.setLocation(new ClassPathResource("lua/auction-close-request.lua"));
        script.setResultType(String.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 마감_요청은_auction_state를_ENDED로_바꾸고_1시간에서_6시간_사이_TTL을_건다() {
        redisTemplate.opsForHash().putAll("auction:state:11", Map.of(
                "status", "ENDING", "closeTime", "2026-08-12T01:00:00Z", "closeTimeEpochMillis", "1786496400000",
                "sellerId", "7", "itemId", "10", "startPrice", "40000", "currentPrice", "50000",
                "bidIncrement", "3000", "bidCount", "3"
        ));
        redisTemplate.opsForZSet().add("auction:active:by-close-time", "11", 1786496400000.0);

        String result = redisTemplate.execute(script, List.of("auction:state:11", "event:timeline"),
                "11", "2026-08-12T01:00:00Z", "1786496400000");

        assertThat(result).startsWith("ACCEPTED|");
        assertThat(redisTemplate.opsForHash().get("auction:state:11", "status")).isEqualTo("ENDED");
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "11")).isNull();
        assertThat(redisTemplate.getExpire("auction:state:11")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 중복_마감_요청은_무시한다() {
        redisTemplate.opsForHash().putAll("auction:state:11", Map.of(
                "status", "ENDED", "closeTime", "2026-08-12T01:00:00Z", "closeTimeEpochMillis", "1786496400000",
                "closeRequestedAt", "2026-08-12T01:00:00Z", "sellerId", "7", "itemId", "10",
                "startPrice", "40000", "currentPrice", "50000", "bidIncrement", "3000", "bidCount", "3"
        ));

        String result = redisTemplate.execute(script, List.of("auction:state:11", "event:timeline"),
                "11", "2026-08-12T02:00:00Z", "1786500000000");

        assertThat(result).isEqualTo("REPLAYED");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isZero();
    }
}
