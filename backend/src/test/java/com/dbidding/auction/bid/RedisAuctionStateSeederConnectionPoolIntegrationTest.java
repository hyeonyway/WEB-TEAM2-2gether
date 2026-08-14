package com.dbidding.auction.bid;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.dashboard.RedisDashboardStateSeeder;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("redis")
@SpringJUnitConfig(classes = {
        RedisAuctionStateSeeder.class,
        RedisDashboardStateSeeder.class,
        RedisStateSingleFlight.class,
        RedisAuctionStateSeederConnectionPoolIntegrationTest.TransactionTestConfiguration.class
})
class RedisAuctionStateSeederConnectionPoolIntegrationTest {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired private RedisAuctionStateSeeder stateSeeder;
    @Autowired private RedisDashboardStateSeeder dashboardStateSeeder;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private HikariDataSource dataSource;

    @MockitoBean private StringRedisTemplate redisTemplate;
    @MockitoBean private RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    @MockitoBean private BidRepository bidRepository;
    @MockitoBean private RedisAuctionSeedBatchCoordinator batchCoordinator;
    @MockitoBean(name = "auctionStateSeedScript") private RedisScript<Long> auctionStateSeedScript;

    private final ExecutorService batchExecutor = Executors.newFixedThreadPool(2);

    @AfterEach
    void shutdownExecutor() throws InterruptedException {
        batchExecutor.shutdownNow();
        batchExecutor.awaitTermination(2, SECONDS);
    }

    @Test
    @Timeout(10)
    void pool_size와_같은_동시_cold_seed도_batch_DB_커넥션을_막지_않는다() throws Exception {
        given(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString())).willReturn(false);
        given(projectionCatchUpVerifier.isCaughtUp()).willReturn(true);
        given(batchCoordinator.requestSeedData(anyInt())).willAnswer(invocation ->
                CompletableFuture.supplyAsync(() -> {
                    jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                    return Optional.<AuctionSeedData>empty();
                }, batchExecutor));
        ExecutorService requestExecutor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = requestExecutor.submit(() -> seedAfter(ready, start, 101));
            Future<Boolean> second = requestExecutor.submit(() -> seedAfter(ready, start, 102));
            assertThat(ready.await(2, SECONDS)).isTrue();

            start.countDown();

            assertThat(first.get(3, SECONDS)).isFalse();
            assertThat(second.get(3, SECONDS)).isFalse();
            assertThat(dataSource.getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
        } finally {
            requestExecutor.shutdownNow();
            requestExecutor.awaitTermination(2, SECONDS);
        }
    }

    @Test
    void dashboard_참여_경매_조회_후에는_트랜잭션_없이_cold_seed를_호출한다() {
        AtomicBoolean transactionActiveAtSeed = new AtomicBoolean();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        given(redisTemplate.hasKey("auction:dashboard:seeded:77")).willReturn(false);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);
        given(projectionCatchUpVerifier.isCaughtUp()).willAnswer(invocation -> {
            transactionActiveAtSeed.set(TransactionSynchronizationManager.isActualTransactionActive());
            return true;
        });
        given(bidRepository.findDistinctAuctionByBidderIdAndAuctionStatusIn(
                77, java.util.List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)))
                .willReturn(java.util.List.of());

        dashboardStateSeeder.seedIfRequired(77);

        assertThat(transactionActiveAtSeed).isFalse();
    }

    private boolean seedAfter(
            CountDownLatch ready, CountDownLatch start, Integer auctionId
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return stateSeeder.seedIfAbsent(auctionId);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement
    @Profile("redis")
    static class TransactionTestConfiguration {
        @Bean
        HikariDataSource dataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(MYSQL.getJdbcUrl());
            config.setUsername(MYSQL.getUsername());
            config.setPassword(MYSQL.getPassword());
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(500);
            return new HikariDataSource(config);
        }

        @Bean
        PlatformTransactionManager transactionManager(HikariDataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(HikariDataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }
    }
}
