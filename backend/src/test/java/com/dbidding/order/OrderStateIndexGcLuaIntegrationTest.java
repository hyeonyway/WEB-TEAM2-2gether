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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class OrderStateIndexGcLuaIntegrationTest {
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
        script.setLocation(new ClassPathResource("lua/order-state-index-gc.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() { if (connectionFactory != null) connectionFactory.destroy(); }

    @Test
    void order_state가_남아있는_auctionId는_srem하지_않는다() {
        redisTemplate.opsForSet().add("order:state:buyer:7", "1");
        redisTemplate.opsForHash().put("order:state:1", "status", "PENDING_CONFIRM");

        Long removed = redisTemplate.execute(script, List.of("order:state:buyer:7"));

        assertThat(removed).isEqualTo(0L);
        assertThat(redisTemplate.opsForSet().members("order:state:buyer:7")).containsExactly("1");
    }

    @Test
    void order_state가_이미_만료된_auctionId만_srem한다() {
        redisTemplate.opsForSet().add("order:state:seller:7", "1", "2");
        redisTemplate.opsForHash().put("order:state:2", "status", "COMPLETED");
        // order:state:1은 없음 - 정산 완료 후 TTL이 지나 이미 사라진 상태를 흉내낸다.

        Long removed = redisTemplate.execute(script, List.of("order:state:seller:7"));

        assertThat(removed).isEqualTo(1L);
        assertThat(redisTemplate.opsForSet().members("order:state:seller:7")).containsExactly("2");
    }

    @Test
    void 여러_유저_인덱스를_한번에_전달하면_각각_정리한다() {
        redisTemplate.opsForSet().add("order:state:buyer:7", "1", "2");
        redisTemplate.opsForSet().add("order:state:seller:9", "1");
        redisTemplate.opsForHash().put("order:state:2", "status", "COMPLETED");
        // order:state:1은 없음 - 정산 완료 후 TTL이 지나 이미 사라진 상태를 흉내낸다.

        Long removed = redisTemplate.execute(script, List.of("order:state:buyer:7", "order:state:seller:9"));

        assertThat(removed).isEqualTo(2L);
        assertThat(redisTemplate.opsForSet().members("order:state:buyer:7")).containsExactly("2");
        assertThat(redisTemplate.opsForSet().members("order:state:seller:9")).isEmpty();
    }
}
