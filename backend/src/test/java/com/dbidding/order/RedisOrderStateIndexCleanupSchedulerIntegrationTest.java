package com.dbidding.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** SCAN 기반 스케줄러가 실제로 order:state:buyer/seller:* 키를 끝까지 정리하는지 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
class RedisOrderStateIndexCleanupSchedulerIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisOrderStateIndexCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        DefaultRedisScript<Long> gcScript = new DefaultRedisScript<>();
        gcScript.setLocation(new ClassPathResource("lua/order-state-index-gc.lua"));
        gcScript.setResultType(Long.class);

        scheduler = new RedisOrderStateIndexCleanupScheduler(redisTemplate, gcScript);
        ReflectionTestUtils.setField(scheduler, "scanLimit", 5000);
        ReflectionTestUtils.setField(scheduler, "batchSize", 1);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 종료된_주문의_auctionId만_buyer_seller_인덱스에서_정리한다() {
        redisTemplate.opsForSet().add("order:state:buyer:7", "1", "2");
        redisTemplate.opsForSet().add("order:state:seller:9", "1", "3");
        redisTemplate.opsForHash().put("order:state:2", "status", "PENDING_CONFIRM");
        redisTemplate.opsForHash().put("order:state:3", "status", "PENDING_CONFIRM");
        // order:state:1은 없음(=완료/취소 후 TTL로 이미 만료됨)

        scheduler.removeOrphanedIndexEntries();

        assertThat(redisTemplate.opsForSet().members("order:state:buyer:7")).containsExactly("2");
        assertThat(redisTemplate.opsForSet().members("order:state:seller:9")).containsExactly("3");
    }

    @Test
    void 정리할_orphan_auctionId가_없으면_아무것도_지우지_않는다() {
        redisTemplate.opsForSet().add("order:state:buyer:7", "2");
        redisTemplate.opsForHash().put("order:state:2", "status", "COMPLETED");

        scheduler.removeOrphanedIndexEntries();

        assertThat(redisTemplate.opsForSet().members("order:state:buyer:7")).containsExactly("2");
    }

    @Test
    void 배치_크기보다_많은_유저_인덱스도_배치_경계와_무관하게_전부_정리한다() {
        // batchSize=1이라 buyer 3명 + seller 3명은 각각 최소 6번의 배치로 나뉘어 처리된다.
        for (int userId = 1; userId <= 3; userId++) {
            redisTemplate.opsForSet().add("order:state:buyer:" + userId, "100", String.valueOf(900 + userId));
            redisTemplate.opsForSet().add("order:state:seller:" + userId, "100", String.valueOf(900 + userId));
            redisTemplate.opsForHash().put("order:state:" + (900 + userId), "status", "PENDING_CONFIRM");
        }
        // order:state:100은 없음(=완료/취소 후 TTL로 이미 만료됨) - 모든 유저 인덱스가 이 auctionId를 공유

        scheduler.removeOrphanedIndexEntries();

        for (int userId = 1; userId <= 3; userId++) {
            assertThat(redisTemplate.opsForSet().members("order:state:buyer:" + userId))
                    .containsExactly(String.valueOf(900 + userId));
            assertThat(redisTemplate.opsForSet().members("order:state:seller:" + userId))
                    .containsExactly(String.valueOf(900 + userId));
        }
    }
}
