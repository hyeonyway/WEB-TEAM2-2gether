package com.dbidding.auction.service;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("redis")
@SpringBootTest(properties = {
        "statistic.scheduler.enabled=false",
        "auction.closing.scheduler.enabled=false",
        "auction.deadline.scheduler.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=2",
        "spring.datasource.hikari.minimum-idle=0",
        "spring.datasource.hikari.connection-timeout=500",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate"
})
class AuctionQueryConnectionPoolStarvationIntegrationTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private AuctionQueryService auctionQueryService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private HikariDataSource dataSource;

    @MockitoBean
    private RedisAuctionRealtimeStateReader realtimeStateReader;
    @MockitoBean
    private WalletService walletService;

    @Test
    void Redis_hit_조회는_트랜잭션과_JDBC_커넥션을_잡지_않는다() {
        stubRedisHit();
        AtomicBoolean transactionActive = new AtomicBoolean();
        AtomicBoolean connectionBoundToRequestThread = new AtomicBoolean();
        given(walletService.getBalance(7)).willAnswer(invocation -> {
            transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
            connectionBoundToRequestThread.set(TransactionSynchronizationManager.hasResource(dataSource));
            return new WalletBalanceResponse(100_000L, 20_000L, 80_000L);
        });

        BidResponses.BidContext result = auctionQueryService.getBidContext(7, 101);

        assertThat(result.auctionId()).isEqualTo(101);
        assertThat(transactionActive).isFalse();
        assertThat(connectionBoundToRequestThread).isFalse();
    }

    @Test
    @Timeout(10)
    void 제한된_커넥션_풀에서도_Redis_요청과_별도_DB_batch가_함께_완료된다() throws Exception {
        stubRedisHit();
        ExecutorService batchExecutor = Executors.newFixedThreadPool(2);
        ExecutorService requestExecutor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            given(walletService.getBalance(anyInt())).willAnswer(invocation -> {
                Future<Integer> dbBatch = batchExecutor.submit(
                        () -> jdbcTemplate.queryForObject("SELECT 1", Integer.class));
                int value = dbBatch.get(2, SECONDS);
                return new WalletBalanceResponse(value, 0L, value);
            });
            List<Future<BidResponses.BidContext>> requests = List.of(
                    requestExecutor.submit(() -> awaitAndGet(ready, start, 7, 101)),
                    requestExecutor.submit(() -> awaitAndGet(ready, start, 8, 102))
            );
            assertThat(ready.await(2, SECONDS)).isTrue();

            start.countDown();

            assertThat(requests).allSatisfy(request ->
                    assertThat(request.get(3, SECONDS).auctionId()).isIn(101, 102));
            awaitNoPendingConnections();
        } finally {
            requestExecutor.shutdownNow();
            batchExecutor.shutdownNow();
            requestExecutor.awaitTermination(2, TimeUnit.SECONDS);
            batchExecutor.awaitTermination(2, TimeUnit.SECONDS);
        }
    }

    private BidResponses.BidContext awaitAndGet(
            CountDownLatch ready, CountDownLatch start, Integer userId, Integer auctionId
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        return auctionQueryService.getBidContext(userId, auctionId);
    }

    private void stubRedisHit() {
        Instant closeTime = Instant.parse("2026-08-15T00:00:00Z");
        given(realtimeStateReader.readStoredAuctionState(anyInt())).willAnswer(invocation -> {
            var state = new RedisAuctionRealtimeStateReader.AuctionState(
                    invocation.getArgument(0), AuctionStatus.OPEN, 1, 1,
                    "카드", "세트", "10", "JP", "/card.png",
                    "경매", "설명", null, null, null, false,
                    10_000L, 12_000L, 1_000L, 2, null, 0L,
                    closeTime.minusSeconds(3600), closeTime, List.of("/auction.png")
            );
            return new RedisAuctionRealtimeStateReader.StoredAuctionState(state, null);
        });
        given(realtimeStateReader.read(
                any(RedisAuctionRealtimeStateReader.StoredAuctionState.class), anyInt()
        )).willReturn(new RedisAuctionRealtimeStateReader.RealtimeState(
                AuctionStatus.OPEN, 12_000L, 1_000L, 2, closeTime, null,
                MyBidStatus.NONE, null, List.of()
        ));
    }

    private HikariDataSource hikariDataSource() {
        return dataSource;
    }

    private void awaitNoPendingConnections() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (hikariDataSource().getHikariPoolMXBean().getThreadsAwaitingConnection() != 0
                && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(hikariDataSource().getHikariPoolMXBean().getThreadsAwaitingConnection()).isZero();
    }
}
