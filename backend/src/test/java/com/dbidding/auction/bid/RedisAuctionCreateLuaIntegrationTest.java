package com.dbidding.auction.bid;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisAuctionCreateLuaIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisAuctionCreateExecutor executor;

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
        script.setLocation(new ClassPathResource("lua/auction-create.lua"));
        script.setResultType(String.class);
        executor = new RedisAuctionCreateExecutor(redisTemplate, script,
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC));
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void 경매_생성은_Redis_상태와_생성_Stream_이벤트를_원자적으로_기록한다() {
        RedisAuctionCreateResult result = executor.execute(command("create-1"));

        assertThat(result.auctionId()).isEqualTo(1);
        assertThat(redisTemplate.opsForHash().entries("auction:state:1"))
                .containsEntry("status", "OPEN")
                .containsEntry("sellerId", "7")
                .containsEntry("cardName", "리자몽")
                .containsEntry("currentPrice", "40000")
                .containsEntry("bidCount", "0");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThat(redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, -1)).containsExactly("1");
        var event = redisTemplate.opsForStream()
                .read(StreamOffset.create("event:timeline", ReadOffset.from("0-0")))
                .getFirst().getValue();
        assertThat(event).containsEntry("eventType", "auction.created.v1")
                .containsEntry("auctionId", "1")
                .containsEntry("sellerId", "7")
                .containsEntry("imagePaths", "/auctions/1.png\n/auctions/2.png");

        assertThat(executor.execute(command("create-1")).auctionId()).isEqualTo(1);
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    private RedisAuctionCreateCommand command(String idempotencyKey) {
        return new RedisAuctionCreateCommand(7, 10, "리자몽", "10", "JP", "/cards/charizard.png", "리자몽 경매", "설명", "메모", null, "NM", false,
                40_000L, 80_000L, 3_000L, 1_000L, List.of("/auctions/1.png", "/auctions/2.png"),
                Instant.parse("2026-08-12T12:00:00Z"), idempotencyKey, "a".repeat(64));
    }
}
