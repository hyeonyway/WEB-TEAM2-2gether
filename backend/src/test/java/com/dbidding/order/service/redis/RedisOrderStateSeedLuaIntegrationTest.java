package com.dbidding.order.service.redis;

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
class RedisOrderStateSeedLuaIntegrationTest {
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
        script.setLocation(new ClassPathResource("lua/order-state-seed.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void MySQL_projection_주문은_Redis_state와_orderId_index를_한번만_생성한다() {
        List<String> keys = List.of("order:state:10", "order:state:by-order-id:100", "order:state:buyer:1", "order:state:seller:7");

        assertThat(redisTemplate.execute(script, keys, "100", "10", "1", "7", "리자몽", "50000", "PENDING_CONFIRM", "2026-08-12T00:00:00Z"))
                .isEqualTo(1L);
        assertThat(redisTemplate.execute(script, keys, "100", "10", "1", "7", "다른 카드", "100", "COMPLETED", "2026-08-12T01:00:00Z"))
                .isZero();

        assertThat(redisTemplate.opsForHash().entries("order:state:10"))
                .containsEntry("orderId", "100").containsEntry("status", "PENDING_CONFIRM").containsEntry("orderVersion", "0");
        assertThat(redisTemplate.opsForValue().get("order:state:by-order-id:100")).isEqualTo("10");
        assertThat(redisTemplate.getExpire("order:state:10")).isEqualTo(-1L);
        assertThat(redisTemplate.getExpire("order:state:by-order-id:100")).isEqualTo(-1L);
    }

    @Test
    void 완료_또는_취소_상태의_재시딩은_order_state와_by_order_id에_1시간에서_6시간_사이_TTL을_건다() {
        List<String> keys = List.of("order:state:10", "order:state:by-order-id:100", "order:state:buyer:1", "order:state:seller:7");

        assertThat(redisTemplate.execute(script, keys, "100", "10", "1", "7", "리자몽", "50000", "COMPLETED", "2026-08-12T00:00:00Z"))
                .isEqualTo(1L);

        assertThat(redisTemplate.getExpire("order:state:10")).isBetween(3600L, 21600L);
        assertThat(redisTemplate.getExpire("order:state:by-order-id:100")).isBetween(3600L, 21600L);
    }
}
