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
class RedisAuctionStateSeedLuaIntegrationTest {
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
        script.setLocation(new ClassPathResource("lua/auction-state-seed.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void 기존_Redis_경매_state는_MySQL_seed로_덮어쓰지_않는다() {
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");
        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "2", "status", "OPEN", "currentPrice", "100", "0", "0", "0", "100", "0", "500")).isEqualTo(1L);
        assertThat(redisTemplate.execute(script, keys,
                "2000", "1", "2", "status", "ENDING", "currentPrice", "200", "0", "0", "0", "200", "0", "600")).isZero();

        assertThat(redisTemplate.opsForHash().get("auction:state:1", "currentPrice")).isEqualTo("100");
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "1")).isEqualTo(1000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(100D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(500D);
    }

    @Test
    void state_생성과_함께_사용자_입찰상태와_최근_입찰을_기록한다() {
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");

        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "3", "status", "OPEN", "estimatedCloseTime", "1970-01-01T00:00:01Z", "estimatedCloseTimeEpochMillis", "1000",
                "2", "10", "OUTBID", "40000", "20", "LEADING", "43000",
                "2", "101", "10", "40000", "101", "2026-08-13T00:00:00Z", "102", "20", "43000", "102", "2026-08-13T00:01:00Z",
                "2", "43000", "750", "900"
        )).isEqualTo(1L);

        assertThat(redisTemplate.opsForHash().entries("auction:bidder:1:10"))
                .containsEntry("status", "OUTBID").containsEntry("amount", "40000");
        assertThat(redisTemplate.opsForSet().members("auction:dashboard:participating:10")).containsExactly("1");
        assertThat(redisTemplate.opsForStream().size("auction:recent-bids:1")).isEqualTo(2L);
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1")).isEqualTo(-299000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "1")).isEqualTo(2D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(43000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "1")).isEqualTo(750D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(900D);
    }

    @Test
    void 이미_종료된_경매를_콜드시드하면_state에도_TTL을_건다() {
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");

        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "2", "status", "ENDED", "currentPrice", "100", "0", "0", "0", "100", "0", "500")).isEqualTo(1L);

        Long ttl = redisTemplate.getExpire("auction:state:1");
        assertThat(ttl).isGreaterThan(0L).isLessThanOrEqualTo(3601L);
    }

    @Test
    void 이미_종료된_경매를_콜드시드해도_활성_인덱스_5종에는_넣지_않는다() {
        // state TTL(최대 6시간)이 활성 인덱스 GC의 24시간 staleness 기준보다 짧아, 여기에 들어가면
        // state가 먼저 사라져 GC가 status를 영영 확인 못 하는 영구 리크가 된다 - 애초에 안 넣어야 한다.
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");

        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "2", "status", "ENDED", "currentPrice", "100", "0", "0", "0", "100", "0", "500")).isEqualTo(1L);

        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "1")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "1")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "1")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isNull();
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1")).isNull();
    }

    @Test
    void ENDING_경매를_콜드시드하면_TTL_없이_활성_인덱스_5종에도_정상적으로_들어간다() {
        // 진행 중(OPEN/ENDING)인 경매까지 활성 인덱스에서 빠지면 목록/스케줄러가 이 경매를 놓치는
        // 회귀가 생긴다 - 종료 상태만 걸러내는 분기가 ENDING까지 잘못 걸러내지 않는지 확인한다.
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");

        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "2", "status", "ENDING", "currentPrice", "200", "0", "0", "0", "200", "0", "600")).isEqualTo(1L);

        assertThat(redisTemplate.getExpire("auction:state:1")).isEqualTo(-1L);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-close-time", "1")).isEqualTo(1000D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-bid-count", "1")).isEqualTo(0D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-price", "1")).isEqualTo(200D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-change-rate", "1")).isEqualTo(0D);
        assertThat(redisTemplate.opsForZSet().score("auction:active:by-open-time", "1")).isEqualTo(600D);
        // ENDING은 OPEN 전용 ending-window에는 들어가지 않는다(기존 동작, 변경 없음).
        assertThat(redisTemplate.opsForZSet().score("auction:ending-window:by-close-time", "1")).isNull();
    }

    @Test
    void OPEN_경매를_콜드시드하면_state에_TTL을_걸지_않는다() {
        List<String> keys = List.of("auction:state:1", "auction:active:by-close-time", "auction:recent-bids:1", "auction:ending-window:by-close-time",
                "auction:active:by-bid-count", "auction:active:by-price", "auction:active:by-change-rate", "auction:active:by-open-time");

        assertThat(redisTemplate.execute(script, keys,
                "1000", "1", "2", "status", "OPEN", "currentPrice", "100", "0", "0", "0", "100", "0", "500")).isEqualTo(1L);

        assertThat(redisTemplate.getExpire("auction:state:1")).isEqualTo(-1L);
    }
}
