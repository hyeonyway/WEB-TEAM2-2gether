package com.dbidding.wallet.service.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class RedisWalletTransitionLuaIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private DefaultRedisScript<String> script;

    @BeforeEach void setUp() {
        if (connectionFactory == null) { connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379)); connectionFactory.afterPropertiesSet(); }
        template = new StringRedisTemplate(connectionFactory); template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        script = new DefaultRedisScript<>(); script.setLocation(new ClassPathResource("lua/wallet-transition.lua")); script.setResultType(String.class);
    }
    @AfterAll static void close() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test void 충전은_잔액과_Stream과_버전을_함께_전이한다() {
        template.opsForHash().putAll("wallet:balance:1", Map.of("availableBalance", "10000", "frozenBalance", "2000", "walletVersion", "4"));
        String result = execute("wallet.charged.v1", "3000", "charge-1");
        assertThat(result).startsWith("ACCEPTED|");
        assertThat(template.opsForHash().get("wallet:balance:1", "availableBalance")).isEqualTo("13000");
        assertThat(template.opsForHash().get("wallet:balance:1", "walletVersion")).isEqualTo("5");
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test void 충전은_지갑_잔액에_TTL을_걸지_않는다() {
        template.opsForHash().putAll("wallet:balance:1", Map.of("availableBalance", "10000", "frozenBalance", "2000", "walletVersion", "4"));
        execute("wallet.charged.v1", "3000", "charge-1");

        assertThat(template.getExpire("wallet:balance:1")).isEqualTo(-1L);
    }

    @Test
    void 큰_잔액과_버전도_Hash_Stream_응답에_일반_정수_문자열로_기록한다() {
        template.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "900000000000", "frozenBalance", "0",
                "walletVersion", "99999999999999"
        ));

        String result = execute("wallet.charged.v1", "100000000000", "large-charge");

        assertThat(result).doesNotContain("e+").doesNotContain("e-");
        assertThat(template.opsForHash().entries("wallet:balance:1"))
                .containsEntry("availableBalance", "1000000000000")
                .containsEntry("frozenBalance", "0")
                .containsEntry("walletVersion", "100000000000000");
        var event = template.opsForStream()
                .read(org.springframework.data.redis.connection.stream.StreamOffset.fromStart("event:timeline"))
                .getFirst().getValue();
        assertThat(event).containsEntry("availableBalance", "1000000000000")
                .containsEntry("frozenBalance", "0")
                .containsEntry("walletVersion", "100000000000000")
                .containsEntry("transactionAmount", "100000000000");

        String replay = execute("wallet.charged.v1", "100000000000", "large-charge");
        assertThat(replay).doesNotContain("e+").doesNotContain("e-").endsWith("|true");
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test
    void 충전으로_총_보유액이_1조원을_넘으면_아무것도_변경하지_않는다() {
        template.opsForHash().putAll("wallet:balance:1", Map.of(
                "availableBalance", "950000000000", "frozenBalance", "0", "walletVersion", "7"
        ));

        String result = execute("wallet.charged.v1", "100000000000", "over-balance");

        assertThat(result).isEqualTo("REJECTED|BALANCE_LIMIT_EXCEEDED");
        assertThat(template.opsForHash().entries("wallet:balance:1"))
                .containsEntry("availableBalance", "950000000000")
                .containsEntry("walletVersion", "7");
        assertThat(template.opsForStream().size("event:timeline")).isZero();
        assertThat(template.hasKey("wallet:idempotency:1:over-balance")).isFalse();
    }

    private String execute(String eventType, String amount, String idempotencyKey) {
        return template.execute(script,
                List.of("wallet:balance:1", "wallet:idempotency:1:" + idempotencyKey, "event:timeline"),
                UUID.randomUUID().toString(), eventType, "1", amount, idempotencyKey, "hash",
                "2026-08-11T00:00:00Z", "100000000000", "1000000000000");
    }
}
