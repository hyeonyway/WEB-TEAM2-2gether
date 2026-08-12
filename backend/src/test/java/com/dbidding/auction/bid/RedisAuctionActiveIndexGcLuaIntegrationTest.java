package com.dbidding.auction.bid;

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
class RedisAuctionActiveIndexGcLuaIntegrationTest {
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
        script.setLocation(new ClassPathResource("lua/auction-active-index-gc.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 오래된_종료_경매만_활성_인덱스에서_제거하고_종료_재시도_대상은_유지한다() {
        redisTemplate.opsForZSet().add("auction:active:by-close-time", "1", 1_000);
        redisTemplate.opsForZSet().add("auction:active:by-close-time", "2", 1_000);
        redisTemplate.opsForHash().put("auction:state:1", "status", "ENDED");
        redisTemplate.opsForHash().put("auction:state:2", "status", "OPEN");

        Long removed = redisTemplate.execute(script, List.of("auction:active:by-close-time"), "2000", "100");

        assertThat(removed).isEqualTo(1L);
        assertThat(redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, -1)).containsExactly("2");
    }
}
