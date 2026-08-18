package com.dbidding.auction.bid.redis;

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
class RedisAuctionSequenceSyncLuaIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

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
        script.setLocation(new ClassPathResource("lua/auction-sequence-sync.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() {
        if (connectionFactory != null) connectionFactory.destroy();
    }

    @Test
    void 카운터가_없으면_목표값으로_세팅한다() {
        Long result = redisTemplate.execute(script, List.of("auction:sequence"), "100");

        assertThat(result).isEqualTo(1L);
        assertThat(redisTemplate.opsForValue().get("auction:sequence")).isEqualTo("100");
    }

    @Test
    void 카운터가_목표값보다_작으면_목표값으로_올린다() {
        redisTemplate.opsForValue().set("auction:sequence", "90");

        Long result = redisTemplate.execute(script, List.of("auction:sequence"), "100");

        assertThat(result).isEqualTo(1L);
        assertThat(redisTemplate.opsForValue().get("auction:sequence")).isEqualTo("100");
    }

    @Test
    void 카운터가_이미_목표값_이상이면_바꾸지_않는다() {
        redisTemplate.opsForValue().set("auction:sequence", "150");

        Long result = redisTemplate.execute(script, List.of("auction:sequence"), "100");

        assertThat(result).isEqualTo(0L);
        assertThat(redisTemplate.opsForValue().get("auction:sequence")).isEqualTo("150");
    }
}
