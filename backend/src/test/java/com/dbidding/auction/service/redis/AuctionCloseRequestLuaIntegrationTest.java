package com.dbidding.auction.service.redis;

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
    void 낙찰자_없이_마감하면_auction_state를_ENDED로_바꾸고_1시간에서_6시간_사이_TTL을_건다() {
        redisTemplate.opsForHash().putAll("auction:state:11", Map.ofEntries(
                Map.entry("status", "ENDING"), Map.entry("sellerId", "7"), Map.entry("itemId", "10"), Map.entry("cardName", "리자몽"),
                Map.entry("startPrice", "40000"), Map.entry("currentPrice", "40000"), Map.entry("bidIncrement", "3000"), Map.entry("bidCount", "0"),
                Map.entry("closeTime", "2026-08-12T01:00:00Z"), Map.entry("closeTimeEpochMillis", "1786496400000")
        ));
        redisTemplate.opsForZSet().add("auction:active:by-close-time", "11", 1786496400000.0);
        redisTemplate.opsForZSet().add("auction:active:by-bid-count", "11", 0.0);
        redisTemplate.opsForZSet().add("auction:active:by-price", "11", 40000.0);
        redisTemplate.opsForZSet().add("auction:active:by-change-rate", "11", 0.0);
        redisTemplate.opsForZSet().add("auction:active:by-open-time", "11", 1786490000000.0);

        String result = redisTemplate.execute(script, List.of("auction:state:11", "event:timeline", "auction:ending-window:by-close-time",
                        "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time"),
                "11", "2026-08-12T01:00:00Z", "1786496400000", "1000000000000");

        assertThat(result).startsWith("ACCEPTED||0|7|10|리자몽");
        assertThat(redisTemplate.opsForHash().get("auction:state:11", "status")).isEqualTo("ENDED");
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "11")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "11")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "11")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "11")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "11")).isNull();
        assertThat(redisTemplate.getExpire("auction:state:11")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 낙찰자가_있으면_지갑_hold를_정산하고_승자_상태를_WON으로_바꾼다() {
        redisTemplate.opsForHash().putAll("auction:state:11", Map.ofEntries(
                Map.entry("status", "ENDING"), Map.entry("sellerId", "7"), Map.entry("itemId", "10"), Map.entry("cardName", "리자몽"),
                Map.entry("startPrice", "40000"), Map.entry("currentPrice", "50000"), Map.entry("bidIncrement", "3000"), Map.entry("bidCount", "3"),
                Map.entry("closeTime", "2026-08-12T01:00:00Z"), Map.entry("closeTimeEpochMillis", "1786496400000"),
                Map.entry("highestBidderId", "2"), Map.entry("highestHoldAmount", "50000")
        ));
        redisTemplate.opsForHash().putAll("wallet:balance:2", Map.of(
                "availableBalance", "40000", "frozenBalance", "50000", "walletVersion", "4"
        ));
        redisTemplate.opsForZSet().add("auction:ending-window:by-close-time", "11", 1786496100000.0);

        String result = redisTemplate.execute(script, List.of("auction:state:11", "event:timeline", "auction:ending-window:by-close-time",
                        "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time"),
                "11", "2026-08-12T01:00:00Z", "1786496400000", "1000000000000");

        assertThat(result).startsWith("ACCEPTED|2|50000|7|10|리자몽");
        assertThat(redisTemplate.opsForHash().get("auction:bidder:11:2", "status")).isEqualTo("WON");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:2", "frozenBalance")).isEqualTo("0");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:2", "walletVersion")).isEqualTo("5");
        assertThat(redisTemplate.hasKey("wallet:hold:11:2")).isFalse();
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "11")).isNull();
    }

    @Test
    void 중복_마감_요청은_REPLAYED를_반환한다() {
        redisTemplate.opsForHash().putAll("auction:state:11", Map.of(
                "status", "ENDED", "closeTime", "2026-08-12T01:00:00Z", "closeTimeEpochMillis", "1786496400000",
                "closeRequestedAt", "2026-08-12T01:00:00Z", "sellerId", "7", "itemId", "10",
                "startPrice", "40000", "currentPrice", "50000", "bidIncrement", "3000", "bidCount", "3"
        ));

        String result = redisTemplate.execute(script, List.of("auction:state:11", "event:timeline", "auction:ending-window:by-close-time",
                        "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time"),
                "11", "2026-08-12T02:00:00Z", "1786500000000", "1000000000000");

        assertThat(result).isEqualTo("REPLAYED");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isZero();
    }
}
