package com.dbidding.auction.bid.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dbidding.auction.bid.dto.RedisAuctionCreateCommand;
import com.dbidding.auction.bid.dto.RedisAuctionCreateResult;
import com.dbidding.auction.exception.AuctionException;
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
                .containsEntry("cardSetName", "base")
                .containsEntry("currentPrice", "40000")
                .containsEntry("bidCount", "0")
                .containsEntry("estimatedCloseTime", "2026-08-12T12:00:00Z")
                .containsEntry("estimatedCloseTimeEpochMillis", "1786536000000");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
        assertThat(redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, -1)).containsExactly("1");
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1"))
                .isEqualTo(1786535700000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "1")).isEqualTo(0D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(40000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "1")).isEqualTo(0D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(1786492800000D);
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

    @Test
    void 이미_존재하는_ID와_충돌하면_기존_경매_state를_덮어쓰지_않는다() {
        // auction:sequence가 어떤 이유로든(예: 카운터 리셋) 이미 활성 경매가 쓰고 있는 ID 바로 앞
        // 값을 가리키면, 다음 INCR이 그 경매와 같은 ID를 내놓는다. auction-create.lua는 이제 HSET
        // 전에 EXISTS를 확인해서, 충돌하면 생성 자체를 거부하고 살아있는 state를 보존한다.
        redisTemplate.opsForHash().putAll("auction:state:5", java.util.Map.of(
                "status", "OPEN", "sellerId", "1", "sequence", "619", "currentPrice", "682841", "bidCount", "624"
        ));
        redisTemplate.opsForValue().set("auction:sequence", "4");

        assertThatThrownBy(() -> executor.execute(command("collide-1")))
                .isInstanceOf(AuctionException.class);

        assertThat(redisTemplate.opsForHash().entries("auction:state:5"))
                .containsEntry("sequence", "619")
                .containsEntry("currentPrice", "682841");
    }

    private RedisAuctionCreateCommand command(String idempotencyKey) {
        return new RedisAuctionCreateCommand(7, 10, "리자몽", "base", "10", "JP", "/cards/charizard.png", "리자몽 경매", "설명", "메모", null, "NM", false,
                40_000L, 80_000L, 3_000L, 1_000L, List.of("/auctions/1.png", "/auctions/2.png"),
                Instant.parse("2026-08-12T12:00:00Z"), idempotencyKey, "a".repeat(64));
    }
}
