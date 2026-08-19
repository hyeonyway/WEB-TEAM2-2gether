package com.dbidding.auction.service;

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

/** SCAN 기반 스케줄러가 실제로 auction:bidder:* / auction:dashboard:participating:* 키를 끝까지 정리하는지 확인한다. */
@Testcontainers(disabledWithoutDocker = true)
class RedisBidderStateCleanupSchedulerIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private RedisBidderStateCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();

        DefaultRedisScript<Long> bidderStateGcScript = new DefaultRedisScript<>();
        bidderStateGcScript.setLocation(new ClassPathResource("lua/auction-bidder-state-gc.lua"));
        bidderStateGcScript.setResultType(Long.class);
        DefaultRedisScript<Long> participatingGcScript = new DefaultRedisScript<>();
        participatingGcScript.setLocation(new ClassPathResource("lua/auction-dashboard-participating-gc.lua"));
        participatingGcScript.setResultType(Long.class);

        scheduler = new RedisBidderStateCleanupScheduler(redisTemplate, bidderStateGcScript, participatingGcScript);
        ReflectionTestUtils.setField(scheduler, "scanLimit", 5000);
        ReflectionTestUtils.setField(scheduler, "batchSize", 2);
        ReflectionTestUtils.setField(scheduler, "participatingBatchSize", 2);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 변경_전에는_state가_사라져도_bidder_참여_키가_영구_잔존한다() {
        // auction:state를 아예 만들지 않고 bidder/participating만 심어, "종료 후 TTL이 지나 state가 사라진" 상황을 흉내낸다.
        redisTemplate.opsForHash().put("auction:bidder:1:10", "status", "OUTBID");
        redisTemplate.opsForSet().add("auction:dashboard:participating:10", "1");

        // 이 스케줄러를 호출하지 않으면(=변경 전 동작) 두 키 모두 그대로 남아있다.
        assertThat(redisTemplate.hasKey("auction:bidder:1:10")).isTrue();
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:10")).containsExactly("1");
    }

    @Test
    void 변경_후에는_state가_사라진_경매의_bidder_참여_키를_스캔으로_찾아_정리한다() {
        // 낙찰자(2)와 OUTBID 참여자(10, 11) 모두 종료된 경매(1)에 남아있고, 진행중 경매(2)에는 참여자(10)가 있다.
        redisTemplate.opsForHash().put("auction:bidder:1:2", "status", "WON");
        redisTemplate.opsForHash().put("auction:bidder:1:10", "status", "OUTBID");
        redisTemplate.opsForHash().put("auction:bidder:1:11", "status", "OUTBID");
        redisTemplate.opsForHash().put("auction:bidder:2:10", "status", "LEADING");
        redisTemplate.opsForSet().add("auction:dashboard:participating:2", "1");
        redisTemplate.opsForSet().add("auction:dashboard:participating:10", "1", "2");
        redisTemplate.opsForSet().add("auction:dashboard:participating:11", "1");
        redisTemplate.opsForStream().add("auction:recent-bids:1", java.util.Map.of("bidderId", "2"));
        redisTemplate.opsForStream().add("auction:recent-bids:2", java.util.Map.of("bidderId", "10"));
        redisTemplate.opsForHash().put("auction:state:2", "status", "OPEN"); // auction:state:1은 없음(=이미 만료됨)

        scheduler.removeOrphanedBidderState();

        // 종료된 경매(1)의 낙찰자/참여자 bidder 키는 낙찰 여부와 무관하게 전부 정리된다.
        assertThat(redisTemplate.hasKey("auction:bidder:1:2")).isFalse();
        assertThat(redisTemplate.hasKey("auction:bidder:1:10")).isFalse();
        assertThat(redisTemplate.hasKey("auction:bidder:1:11")).isFalse();
        // 진행중 경매(2)의 bidder 키는 그대로 남는다.
        assertThat(redisTemplate.hasKey("auction:bidder:2:10")).isTrue();

        // participating SET에서도 종료된 경매(1)만 빠지고, 진행중 경매(2)는 남는다.
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:2")).isEmpty();
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:10")).containsExactly("2");
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:11")).isEmpty();

        // 종료된 경매(1)의 최근 입찰 스트림은 지워지고, 진행중 경매(2)의 스트림은 남는다.
        assertThat(redisTemplate.hasKey("auction:recent-bids:1")).isFalse();
        assertThat(redisTemplate.hasKey("auction:recent-bids:2")).isTrue();
    }

    @Test
    void 정리할_orphan_키가_없으면_아무것도_지우지_않고_조용히_끝난다() {
        redisTemplate.opsForHash().put("auction:bidder:2:10", "status", "LEADING");
        redisTemplate.opsForSet().add("auction:dashboard:participating:10", "2");
        redisTemplate.opsForHash().put("auction:state:2", "status", "OPEN");

        scheduler.removeOrphanedBidderState();

        assertThat(redisTemplate.hasKey("auction:bidder:2:10")).isTrue();
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:10")).containsExactly("2");
    }
}
