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
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
        var event = redisTemplate.opsForStream()
                .read(StreamOffset.create("event:timeline", ReadOffset.from("0-0")))
                .getFirst()
                .getValue();
        assertThat(event).containsEntry("schemaVersion", "1")
                .containsEntry("eventType", "bid.accepted.v1")
                .containsEntry("auctionId", "1")
                .containsEntry("auctionVersion", "7")
                .containsEntry("idempotencyKey", "request-1")
                .containsEntry("auctionStatus", "OPEN");

        executor.execute(new BidCommand(2, 1, 43_000L, "request-1"));

        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThatThrownBy(() -> executor.execute(new BidCommand(2, 1, 46_000L, "request-1")))
                .hasMessage("같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
    }
    @Test
    void 상회입찰은_새_입찰자와_이전_입찰자_지갑에_1시간에서_6시간_사이_idle_TTL을_건다() {
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

        executor.execute(new BidCommand(2, 1, 43_000L, "request-ttl"));

        assertThat(redisTemplate.getExpire("wallet:balance:2")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.getExpire("wallet:balance:1")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.getExpire("auction:state:1")).isEqualTo(-1L);
    }

    @Test
    void 최근_입찰_Stream은_최대_50개만_보관한다() {
        redisTemplate.opsForHash().putAll("auction:state:1", Map.of(
                "status", "OPEN", "sellerId", "999", "currentPrice", "0", "bidIncrement", "1",
                "closeTime", "2026-08-10T01:00:00Z", "closeTimeEpochMillis", "1786323600000",
                "highestBidderId", "", "highestHoldAmount", "0", "sequence", "0", "bidCount", "0"
        ));
        for (int bidderId = 1; bidderId <= 55; bidderId++) {
            redisTemplate.opsForHash().putAll("wallet:balance:" + bidderId, Map.of(
                    "availableBalance", "1000000", "frozenBalance", "0", "walletVersion", "0"
            ));
            executor.execute(new BidCommand(bidderId, 1, (long) bidderId, "bounded-" + bidderId));
        }

        assertThat(redisTemplate.opsForStream().size("auction:recent-bids:1")).isEqualTo(50L);
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(55L);
    }

    @Test
    void 즉시낙찰은_같은_timeline_event와_주문_상태를_원자적으로_생성한다() {
        redisTemplate.opsForHash().putAll("auction:state:1", Map.ofEntries(
                Map.entry("status", "OPEN"), Map.entry("sellerId", "7"), Map.entry("cardName", "리자몽"),
                Map.entry("currentPrice", "40000"), Map.entry("bidIncrement", "3000"), Map.entry("buyNowPrice", "50000"),
                Map.entry("closeTime", "2026-08-10T01:00:00Z"), Map.entry("closeTimeEpochMillis", "1786323600000"),
                Map.entry("highestBidderId", "1"), Map.entry("highestHoldAmount", "40000"),
                Map.entry("sequence", "6"), Map.entry("bidCount", "2")
        ));
        redisTemplate.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "60000", "frozenBalance", "40000", "walletVersion", "4"
        ));

        var response = executor.execute(new BidCommand(1, 1, 99_999L, "buy-now-1"));

        assertThat(response.result().bid().status()).isEqualTo(com.dbidding.auction.domain.BidStatus.WON);
        assertThat(response.result().pendingOrder()).isNotNull();
        assertThat(response.result().pendingOrder().status()).isEqualTo("PENDING");
        assertThat(redisTemplate.opsForHash().entries("auction:state:1"))
                .containsEntry("status", "ENDED").containsEntry("currentPrice", "50000");
        assertThat(redisTemplate.opsForHash().entries("wallet:balance:1"))
                .containsEntry("availableBalance", "50000").containsEntry("frozenBalance", "0");
        assertThat(redisTemplate.opsForHash().entries("order:state:1"))
                .containsEntry("buyerId", "1").containsEntry("sellerId", "7")
                .containsEntry("cardName", "리자몽").containsEntry("status", "PENDING_CONFIRM")
                .containsEntry("projectionStatus", "PENDING");
        assertThat(redisTemplate.opsForSet().members("order:state:buyer:1")).containsExactly("1");
        assertThat(redisTemplate.opsForSet().members("order:state:seller:7")).containsExactly("1");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThat(redisTemplate.getExpire("auction:state:1")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.getExpire("wallet:balance:1")).isBetween(3600L, 21600L);

        executor.execute(new BidCommand(1, 1, 99_999L, "buy-now-1"));

        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }
}
