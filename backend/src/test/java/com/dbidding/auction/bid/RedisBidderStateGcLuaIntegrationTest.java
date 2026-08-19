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
class RedisBidderStateGcLuaIntegrationTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    private static LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate redisTemplate;
    private DefaultRedisScript<Long> bidderStateGcScript;
    private DefaultRedisScript<Long> participatingGcScript;

    @BeforeEach
    void setUp() {
        if (connectionFactory == null) {
            connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
            connectionFactory.afterPropertiesSet();
        }
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
        bidderStateGcScript = new DefaultRedisScript<>();
        bidderStateGcScript.setLocation(new ClassPathResource("lua/auction-bidder-state-gc.lua"));
        bidderStateGcScript.setResultType(Long.class);
        participatingGcScript = new DefaultRedisScript<>();
        participatingGcScript.setLocation(new ClassPathResource("lua/auction-dashboard-participating-gc.lua"));
        participatingGcScript.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void state가_남아있는_경매의_bidder_키는_지우지_않는다() {
        redisTemplate.opsForHash().put("auction:bidder:1:2", "status", "LEADING");
        redisTemplate.opsForHash().put("auction:state:1", "status", "OPEN");

        Long removed = redisTemplate.execute(bidderStateGcScript, List.of("auction:bidder:1:2"), "1");

        assertThat(removed).isEqualTo(0L);
        assertThat(redisTemplate.hasKey("auction:bidder:1:2")).isTrue();
    }

    @Test
    void state가_이미_만료된_경매의_bidder_키는_지운다() {
        redisTemplate.opsForHash().put("auction:bidder:1:2", "status", "WON");
        // auction:state:1은 없음 - 종료 후 TTL이 지나 이미 사라진 상태를 흉내낸다.

        Long removed = redisTemplate.execute(bidderStateGcScript, List.of("auction:bidder:1:2"), "1");

        assertThat(removed).isEqualTo(1L);
        assertThat(redisTemplate.hasKey("auction:bidder:1:2")).isFalse();
    }

    @Test
    void 여러_키를_한번에_전달하면_state_없는_것만_골라서_지운다() {
        redisTemplate.opsForHash().put("auction:bidder:1:2", "status", "WON");
        redisTemplate.opsForHash().put("auction:bidder:3:2", "status", "LEADING");
        redisTemplate.opsForHash().put("auction:state:3", "status", "OPEN");

        Long removed = redisTemplate.execute(bidderStateGcScript,
                List.of("auction:bidder:1:2", "auction:bidder:3:2"), "1", "3");

        assertThat(removed).isEqualTo(1L);
        assertThat(redisTemplate.hasKey("auction:bidder:1:2")).isFalse();
        assertThat(redisTemplate.hasKey("auction:bidder:3:2")).isTrue();
    }

    @Test
    void 같은_스크립트로_recent_bids_스트림도_state_없으면_지운다() {
        redisTemplate.opsForStream().add("auction:recent-bids:1", java.util.Map.of("bidderId", "2"));
        // auction:state:1은 없음 - 종료 후 TTL이 지나 이미 사라진 상태를 흉내낸다.

        Long removed = redisTemplate.execute(bidderStateGcScript, List.of("auction:recent-bids:1"), "1");

        assertThat(removed).isEqualTo(1L);
        assertThat(redisTemplate.hasKey("auction:recent-bids:1")).isFalse();
    }

    @Test
    void participating_set은_state_없는_auctionId만_srem한다() {
        redisTemplate.opsForSet().add("auction:dashboard:participating:2", "1", "3");
        redisTemplate.opsForHash().put("auction:state:3", "status", "OPEN");
        // auction:state:1은 없음 - 종료 후 TTL이 지나 이미 사라진 상태를 흉내낸다.

        Long removed = redisTemplate.execute(participatingGcScript, List.of("auction:dashboard:participating:2"));

        assertThat(removed).isEqualTo(1L);
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:2")).containsExactly("3");
    }

    @Test
    void participating_set도_여러_유저_키를_한번에_처리한다() {
        redisTemplate.opsForSet().add("auction:dashboard:participating:2", "1", "3");
        redisTemplate.opsForSet().add("auction:dashboard:participating:5", "1");
        redisTemplate.opsForHash().put("auction:state:3", "status", "OPEN");
        // auction:state:1은 없음 - 종료 후 TTL이 지나 이미 사라진 상태를 흉내낸다.

        Long removed = redisTemplate.execute(participatingGcScript,
                List.of("auction:dashboard:participating:2", "auction:dashboard:participating:5"));

        assertThat(removed).isEqualTo(2L);
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:2")).containsExactly("3");
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:5")).isEmpty();
    }
}
