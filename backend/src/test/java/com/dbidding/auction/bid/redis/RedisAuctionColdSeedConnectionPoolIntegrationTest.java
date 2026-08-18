package com.dbidding.auction.bid.redis;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("redis")
@DataJpaTest(properties = {
        "spring.sql.init.mode=always",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.connection-timeout=1000"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        AuctionSeedDataLoader.class,
        RedisAuctionSeedBatchCoordinator.class,
        RedisCardStateReader.class,
        RedisAuctionStateSeeder.class,
        RedisStateSingleFlight.class,
        RedisBidLuaConfiguration.class,
        RedisAuctionColdSeedConnectionPoolIntegrationTest.RedisTestConfiguration.class
})
class RedisAuctionColdSeedConnectionPoolIntegrationTest {
    private static final int USER_ID = 99100;
    private static final int CARD_SET_ID = 99100;
    private static final List<Integer> AUCTION_IDS = java.util.stream.IntStream.rangeClosed(99101, 99108)
            .boxed().toList();

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @Autowired private RedisAuctionStateSeeder stateSeeder;
    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private DataSource dataSource;

    @MockitoBean private RedisProjectionCatchUpVerifier projectionCatchUpVerifier;

    @BeforeEach
    void setUp() {
        given(projectionCatchUpVerifier.isCaughtUpForAuctionFresh(org.mockito.ArgumentMatchers.anyInt())).willReturn(true);
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
                VALUES (?, 'cold-seed-pool@test.local', 'cold-seed-pool', 'USER', 'ACTIVE',
                        REPEAT('a', 64), REPEAT('b', 32))
                """, USER_ID);
        jdbcTemplate.update(
                "INSERT INTO card_sets (id, name, code) VALUES (?, 'cold seed pool set', 'COLD-SEED-POOL')",
                CARD_SET_ID);
        for (Integer id : AUCTION_IDS) {
            jdbcTemplate.update(
                    "INSERT INTO card_metadata (id, card_set_id, name, image_path) VALUES (?, ?, ?, ?)",
                    id, CARD_SET_ID, "card-" + id, "/cards/" + id + ".png");
            jdbcTemplate.update("""
                    INSERT INTO auctions (
                        id, user_id, item_id, auction_name, description, start_price, current_price,
                        buy_now_price, delivery_fee, status, open_time, estimated_close_time, close_time,
                        bid_count, bid_price_unit, is_hyped
                    ) VALUES (?, ?, ?, ?, 'cold seed', 10000, 10000, 50000, 0, 'OPEN',
                        UTC_TIMESTAMP(6), DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
                        DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 0, 1000, FALSE)
                    """, id, USER_ID, id, "auction-" + id);
        }
        deleteRedisState();
    }

    @AfterEach
    void tearDown() {
        deleteRedisState();
        for (Integer id : AUCTION_IDS) {
            jdbcTemplate.update("DELETE FROM images WHERE auction_id = ?", id);
            jdbcTemplate.update("DELETE FROM bids WHERE auction_id = ?", id);
            jdbcTemplate.update("DELETE FROM auctions WHERE id = ?", id);
            jdbcTemplate.update("DELETE FROM card_metadata WHERE id = ?", id);
        }
        jdbcTemplate.update("DELETE FROM card_sets WHERE id = ?", CARD_SET_ID);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", USER_ID);
    }

    @Test
    @Timeout(15)
    void 실제_Redis와_MySQL_cold_miss가_pool_size_2에서_모두_완료된다() throws Exception {
        ExecutorService requests = Executors.newFixedThreadPool(AUCTION_IDS.size());
        CountDownLatch ready = new CountDownLatch(AUCTION_IDS.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (Integer auctionId : AUCTION_IDS) {
                results.add(requests.submit(() -> {
                    ready.countDown();
                    start.await();
                    return stateSeeder.seedIfAbsent(auctionId);
                }));
            }
            assertThat(ready.await(3, SECONDS)).isTrue();

            start.countDown();

            assertThat(results).allSatisfy(result -> assertThat(result.get(8, SECONDS)).isTrue());
            assertThat(AUCTION_IDS).allSatisfy(auctionId -> {
                assertThat(redisTemplate.hasKey("auction:state:" + auctionId)).isTrue();
                assertThat(redisTemplate.<Object, Object>opsForHash()
                        .get("auction:state:" + auctionId, "cardName"))
                        .isEqualTo("card-" + auctionId);
            });
            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            assertThat(hikari.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
        } finally {
            requests.shutdownNow();
            requests.awaitTermination(3, SECONDS);
        }
    }

    private void deleteRedisState() {
        List<String> keys = new ArrayList<>();
        for (Integer id : AUCTION_IDS) {
            keys.add("auction:state:" + id);
            keys.add("auction:recent-bids:" + id);
            keys.add("card:cache:" + id);
            String member = String.valueOf(id);
            redisTemplate.opsForZSet().remove("auction:active:by-close-time", member);
            redisTemplate.opsForZSet().remove("auction:ending-window:by-close-time", member);
            redisTemplate.opsForZSet().remove("auction:active:by-bid-count", member);
            redisTemplate.opsForZSet().remove("auction:active:by-price", member);
            redisTemplate.opsForZSet().remove("auction:active:by-change-rate", member);
            redisTemplate.opsForZSet().remove("auction:active:by-open-time", member);
        }
        redisTemplate.delete(keys);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Profile("redis")
    static class RedisTestConfiguration {
        @Bean(destroyMethod = "destroy")
        LettuceConnectionFactory redisConnectionFactory() {
            LettuceConnectionFactory factory = new LettuceConnectionFactory(
                    new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
            factory.afterPropertiesSet();
            return factory;
        }

        @Bean
        StringRedisTemplate stringRedisTemplate(LettuceConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }
    }
}
