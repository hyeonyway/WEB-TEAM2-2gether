package com.dbidding.wallet.service;

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
        String result = template.execute(script, List.of("wallet:balance:1", "wallet:idempotency:1:charge-1", "event:timeline"), UUID.randomUUID().toString(), "wallet.charged.v1", "1", "3000", "charge-1", "hash", "2026-08-11T00:00:00Z");
        assertThat(result).startsWith("ACCEPTED|");
        assertThat(template.opsForHash().get("wallet:balance:1", "availableBalance")).isEqualTo("13000");
        assertThat(template.opsForHash().get("wallet:balance:1", "walletVersion")).isEqualTo("5");
        assertThat(template.opsForStream().size("event:timeline")).isEqualTo(1L);
    }

    @Test void 충전은_지갑_잔액에_TTL을_걸지_않는다() {
        template.opsForHash().putAll("wallet:balance:1", Map.of("availableBalance", "10000", "frozenBalance", "2000", "walletVersion", "4"));
        template.execute(script, List.of("wallet:balance:1", "wallet:idempotency:1:charge-1", "event:timeline"), UUID.randomUUID().toString(), "wallet.charged.v1", "1", "3000", "charge-1", "hash", "2026-08-11T00:00:00Z");

        assertThat(template.getExpire("wallet:balance:1")).isEqualTo(-1L);
    }
}
