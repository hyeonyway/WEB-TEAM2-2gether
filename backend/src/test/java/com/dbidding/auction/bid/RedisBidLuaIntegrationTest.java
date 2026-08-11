package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisBidLuaIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisBidExecutor executor;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/bid-accept.lua"));
        script.setResultType(String.class);
        executor = new RedisBidExecutor(
                redisTemplate,
                script,
                Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
    }

    @Test
    void 승인하면_경매_신규지갑_기존지갑과_Stream을_함께_전이한다() {
        redisTemplate.opsForHash().putAll("auction:state:1", Map.of(
                "status", "OPEN", "sellerId", "7", "currentPrice", "40000", "bidIncrement", "3000",
                "closeTime", "2026-08-10T01:00:00Z", "closeTimeEpochMillis", "1786323600000",
                "highestBidderId", "1", "highestHoldAmount", "40000",
                "sequence", "6", "bidCount", "2"
        ));
        redisTemplate.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "60000", "frozenBalance", "40000", "walletVersion", "4"
        ));
        redisTemplate.opsForHash().putAll("wallet:balance:2", Map.of(
                "availableBalance", "100000", "frozenBalance", "0", "walletVersion", "9"
        ));

        executor.execute(new BidCommand(2, 1, 43_000L, "request-1"));

        assertThat(redisTemplate.opsForHash().get("auction:state:1", "currentPrice")).isEqualTo("43000");
        assertThat(redisTemplate.opsForHash().get("auction:state:1", "sequence")).isEqualTo("7");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:2", "availableBalance")).isEqualTo("57000");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:2", "frozenBalance")).isEqualTo("43000");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:1", "availableBalance")).isEqualTo("100000");
        assertThat(redisTemplate.opsForHash().get("wallet:balance:1", "frozenBalance")).isEqualTo("0");
        assertThat(redisTemplate.opsForHash().get("wallet:hold:1:2", "amount")).isEqualTo("43000");
        assertThat(redisTemplate.opsForStream().size("auction:timeline-events")).isEqualTo(1L);
        var event = redisTemplate.opsForStream()
                .read(StreamOffset.create("auction:timeline-events", ReadOffset.from("0-0")))
                .getFirst()
                .getValue();
        assertThat(event).containsEntry("schemaVersion", "1")
                .containsEntry("eventType", "bid.accepted.v1")
                .containsEntry("auctionId", "1")
                .containsEntry("auctionVersion", "7")
                .containsEntry("idempotencyKey", "request-1")
                .containsEntry("auctionStatus", "OPEN");

        executor.execute(new BidCommand(2, 1, 43_000L, "request-1"));

        assertThat(redisTemplate.opsForStream().size("auction:timeline-events")).isEqualTo(1L);
        assertThatThrownBy(() -> executor.execute(new BidCommand(2, 1, 46_000L, "request-1")))
                .hasMessage("같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
    }
}
