package com.dbidding.auction.service.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
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
class AuctionEndingTransitionLuaIntegrationTest {
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
        script.setLocation(new ClassPathResource("lua/auction-ending-transition.lua"));
        script.setResultType(String.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void due_OPEN_경매를_ENDING으로_한번만_전이하고_예정마감은_유지한다() {
        state("OPEN", "1786320300000");
        redisTemplate.opsForZSet().add("auction:ending-window:by-close-time", "1", 1786320000000D);
        redisTemplate.opsForZSet().add("auction:active:by-close-time", "1", 1786320300000D);

        String result = execute("1786320000000", "2026-08-10T00:00:00Z", "2026-08-10T00:06:30Z", "1786320390000");

        assertThat(result).startsWith("TRANSITIONED|");
        assertThat(redisTemplate.opsForHash().entries("auction:state:1"))
                .containsEntry("status", "ENDING")
                .containsEntry("closeTime", "2026-08-10T00:06:30Z")
                .containsEntry("closeTimeEpochMillis", "1786320390000")
                .containsEntry("estimatedCloseTime", "2026-08-10T00:05:00Z")
                .containsEntry("estimatedCloseTimeEpochMillis", "1786320300000");
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "1")).isEqualTo(1786320390000D);
        assertThat(redisTemplate.opsForStream().read(StreamOffset.create("event:timeline", ReadOffset.from("0-0"))).getFirst().getValue())
                .containsEntry("eventType", "auction.ending-started.v1")
                .containsEntry("closeTime", "2026-08-10T00:06:30Z");
        assertThat(execute("1786320000000", "2026-08-10T00:00:00Z", "2026-08-10T00:07:00Z", "1786320420000"))
                .isEqualTo("NOOP|ENDING");
        assertThat(redisTemplate.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 아직_전이시각_전이면_상태를_바꾸지_않는다() {
        state("OPEN", "1786320300000");

        assertThat(execute("1786319999999", "2026-08-09T23:59:59.999Z", "2026-08-10T00:06:30Z", "1786320390000"))
                .isEqualTo("NOOP|TOO_EARLY");
        assertThat(redisTemplate.opsForHash().get("auction:state:1", "status")).isEqualTo("OPEN");
    }

    @Test
    void 이미_마감된_OPEN_경매는_ENDING으로_되살리지_않고_ending_member만_제거한다() {
        state("OPEN", "1786320000000");
        redisTemplate.opsForZSet().add("auction:ending-window:by-close-time", "1", 1786319700000D);

        assertThat(execute("1786320000000", "2026-08-10T00:00:00Z", "2026-08-10T00:06:30Z", "1786320390000"))
                .isEqualTo("NOOP|EXPIRED");
        assertThat(redisTemplate.opsForHash().get("auction:state:1", "status")).isEqualTo("OPEN");
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1")).isNull();
    }

    private void state(String status, String closeTimeEpochMillis) {
        redisTemplate.opsForHash().putAll("auction:state:1", Map.of(
                "status", status, "closeTime", "2026-08-10T00:05:00Z", "closeTimeEpochMillis", closeTimeEpochMillis,
                "estimatedCloseTime", "2026-08-10T00:05:00Z", "estimatedCloseTimeEpochMillis", closeTimeEpochMillis
        ));
    }

    private String execute(String nowEpochMillis, String nowIso, String newCloseTime, String newCloseTimeEpochMillis) {
        return redisTemplate.execute(script, List.of("auction:state:1", "auction:ending-window:by-close-time", "auction:active:by-close-time", "event:timeline"),
                "1", nowEpochMillis, nowIso, newCloseTime, newCloseTimeEpochMillis);
    }
}
