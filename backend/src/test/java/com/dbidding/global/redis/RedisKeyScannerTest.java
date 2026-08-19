package com.dbidding.global.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class RedisKeyScannerTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 패턴에_맞는_키만_찾고_다른_패턴의_키는_제외한다() {
        redisTemplate.opsForValue().set("scan-test:1", "a");
        redisTemplate.opsForValue().set("scan-test:2", "b");
        redisTemplate.opsForValue().set("other:1", "c");

        List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, "scan-test:*", 100);

        assertThat(keys).containsExactlyInAnyOrder("scan-test:1", "scan-test:2");
    }

    @Test
    void limit보다_많은_키가_있으면_limit만큼만_수집하고_중단한다() {
        for (int i = 0; i < 20; i++) {
            redisTemplate.opsForValue().set("scan-limit-test:" + i, "v");
        }

        List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, "scan-limit-test:*", 5);

        assertThat(keys).hasSize(5);
        assertThat(keys).allMatch(key -> key.startsWith("scan-limit-test:"));
    }

    @Test
    void 매칭되는_키가_없으면_빈_리스트를_반환한다() {
        List<String> keys = RedisKeyScanner.scanKeys(redisTemplate, "no-such-pattern:*", 100);

        assertThat(keys).isEmpty();
    }

    @Test
    void limit을_정상_상한처럼_작게_잡으면_반복_호출해도_항상_같은_앞부분만_수집한다() {
        // scan-limit을 "매 실행마다 처리할 정상적인 배치 크기"로 작게 잡으면 안 되는 이유를 그대로
        // 보여주는 테스트다 - 매번 커서 0에서 새로 시작하므로, 두 번 호출해도 결과가 똑같다(진행이
        // 없다). 실제 스케줄러 설정에서는 scan-limit을 이 용도로 쓰지 않고 안전 상한으로만 둔다.
        for (int i = 0; i < 20; i++) {
            redisTemplate.opsForValue().set("scan-progress-test:" + i, "v");
        }

        List<String> first = RedisKeyScanner.scanKeys(redisTemplate, "scan-progress-test:*", 5);
        List<String> second = RedisKeyScanner.scanKeys(redisTemplate, "scan-progress-test:*", 5);

        assertThat(second).isEqualTo(first);
    }

    @Test
    void scanAndCleanup_ARGV_없이_배치로_나눠_스크립트를_실행하고_결과를_합산한다() {
        RedisScript<Long> countKeysScript = new DefaultRedisScript<>("return #KEYS", Long.class);
        for (int i = 0; i < 7; i++) {
            redisTemplate.opsForValue().set("batch-test:" + i, "v");
        }

        long total = RedisKeyScanner.scanAndCleanup(redisTemplate, "batch-test:*", 100, 3, countKeysScript);

        assertThat(total).isEqualTo(7L);
    }

    @Test
    void scanAndCleanup_키마다_ARGV를_계산해서_KEYS와_나란히_전달한다() {
        // ARGV[i]가 KEYS[i]에서 파생한 값과 정확히 짝지어지는지 확인한다(예: auctionId 추출).
        RedisScript<Long> checkArgvMatchesKeyScript = new DefaultRedisScript<>(
                "local ok = 0 for i, k in ipairs(KEYS) do if ARGV[i] == string.sub(k, -1) then ok = ok + 1 end end return ok",
                Long.class);
        redisTemplate.opsForValue().set("argv-test:1", "v");
        redisTemplate.opsForValue().set("argv-test:2", "v");
        redisTemplate.opsForValue().set("argv-test:3", "v");

        long matched = RedisKeyScanner.scanAndCleanup(redisTemplate, "argv-test:*", 100, 2, checkArgvMatchesKeyScript,
                key -> key.substring(key.length() - 1));

        assertThat(matched).isEqualTo(3L);
    }
}
