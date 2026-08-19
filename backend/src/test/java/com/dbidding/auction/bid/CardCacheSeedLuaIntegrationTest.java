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
class CardCacheSeedLuaIntegrationTest {
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
        script.setLocation(new ClassPathResource("lua/card-cache-seed.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 새_카드는_5개_필드와_TTL을_한번에_원자적으로_적재한다() {
        List<String> keys = List.of("card:cache:10");

        Long result = redisTemplate.execute(script, keys, "리자몽", "base", "10", "JP", "/cards/10.png", "86410");

        assertThat(result).isEqualTo(1L);
        assertThat(redisTemplate.opsForHash().entries("card:cache:10"))
                .containsEntry("name", "리자몽")
                .containsEntry("setName", "base")
                .containsEntry("psaGrade", "10")
                .containsEntry("language", "JP")
                .containsEntry("thumbnailUrl", "/cards/10.png");
        assertThat(redisTemplate.getExpire("card:cache:10")).isEqualTo(86_410L);
    }

    @Test
    void 이미_캐시된_카드는_다시_요청해도_필드를_덮어쓰지_않는다() {
        List<String> keys = List.of("card:cache:11");
        redisTemplate.execute(script, keys, "리자몽", "base", "10", "JP", "/cards/11.png", "86411");

        Long result = redisTemplate.execute(script, keys, "다른이름", "다른세트", "9", "KR", "/cards/other.png", "1");

        assertThat(result).isZero();
        assertThat(redisTemplate.opsForHash().entries("card:cache:11"))
                .containsEntry("name", "리자몽")
                .containsEntry("thumbnailUrl", "/cards/11.png");
        assertThat(redisTemplate.getExpire("card:cache:11")).isEqualTo(86_411L);
    }
}
