package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "statistic.scheduler.enabled=false",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Import(AuctionBidWalletLockOrderConcurrencyTest.WalletTestConfiguration.class)
class AuctionBidWalletLockOrderConcurrencyTest {

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private AuctionCommandService auctionCommandService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CoordinatedWalletService walletService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private AuctionEventPublisher auctionEventPublisher;

    private ExecutorService executor;
    private ConcurrentWalletCalls coordinatedWalletCalls;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        deleteFixtures();
        insertFixtures();
        walletService.resetCoordination();
        coordinatedWalletCalls = walletService.coordinatedWalletCalls();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
        deleteFixtures();
    }

    @RepeatedTest(2)
    void 서로_다른_경매의_교차_outbid는_지갑_락_순서를_맞춰_모두_성공한다() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Long> first = executor.submit(participate(
                2, 1, "bidder-two-auction-one", ready, start
        ));
        Future<Long> second = executor.submit(participate(
                1, 2, "bidder-one-auction-two", ready, start
        ));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(12_000L);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(12_000L);
        assertThat(coordinatedWalletCalls.firstLockTargets()).containsExactly(1);
    }

    @RepeatedTest(5)
    void 교차_즉시낙찰은_구매자와_기존_입찰자와_판매자_지갑을_같은_순서로_잠가_데드락없이_완료한다() throws Exception {
        jdbcTemplate.update("UPDATE wallets SET point = 200000 WHERE user_id IN (1, 2)");
        walletService.disableCoordination();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Future<Long> first = executor.submit(participate(
                2, 1, 100_000L, "buy-now-bidder-two-auction-one", ready, start
        ));
        Future<Long> second = executor.submit(participate(
                1, 2, 100_000L, "buy-now-bidder-one-auction-two", ready, start
        ));

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(100_000L);
        assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(100_000L);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM wallet_holds
                WHERE auction_id IN (1, 2) AND status = 'CAPTURED'
                """, Long.class)).isEqualTo(2L);
    }

    private Callable<Long> participate(
            Integer bidderId,
            Integer auctionId,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return participate(bidderId, auctionId, 12_000L, idempotencyKey, ready, start);
    }

    private Callable<Long> participate(
            Integer bidderId,
            Integer auctionId,
            long price,
            String idempotencyKey,
            CountDownLatch ready,
            CountDownLatch start
    ) {
        return () -> {
            ready.countDown();
            await(start);
            return auctionCommandService.participate(
                    bidderId,
                    auctionId,
                    new BidCreateRequest(price),
                    idempotencyKey
            ).bid().amount();
        };
    }

    private void insertFixtures() {
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
                VALUES
                    (1, 'bidder-one@test.local', 'bidder-one', 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32)),
                    (2, 'bidder-two@test.local', 'bidder-two', 'USER', 'ACTIVE', REPEAT('c', 64), REPEAT('d', 32)),
                    (3, 'seller@test.local', 'seller', 'USER', 'ACTIVE', REPEAT('e', 64), REPEAT('f', 32))
                """);
        jdbcTemplate.update("""
                INSERT INTO card_sets (id, name, code)
                VALUES (1, '락 순서 테스트 세트', 'LOCK-ORDER')
                """);
        jdbcTemplate.update("""
                INSERT INTO card_metadata (id, card_set_id, name)
                VALUES
                    (1, 1, '락 순서 테스트 카드 1'),
                    (2, 1, '락 순서 테스트 카드 2')
                """);
        jdbcTemplate.update("""
                INSERT INTO auctions (
                    id, user_id, item_id, auction_name, description,
                    start_price, current_price, buy_now_price, delivery_fee,
                    status, open_time, estimated_close_time, close_time,
                    bid_count, bid_price_unit, is_hyped
                ) VALUES
                    (1, 3, 1, '교차 경매 1', '교차 경매 테스트 1',
                     10000, 11000, 100000, 0, 'OPEN', UTC_TIMESTAMP(6),
                     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
                     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 1, 1000, FALSE),
                    (2, 3, 2, '교차 경매 2', '교차 경매 테스트 2',
                     10000, 11000, 100000, 0, 'OPEN', UTC_TIMESTAMP(6),
                     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
                     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 1, 1000, FALSE)
                """);
        jdbcTemplate.update("""
                INSERT INTO bids (id, user_id, auction_id, bid_price, status)
                VALUES
                    (1, 1, 1, 11000, 'LEADING'),
                    (2, 2, 2, 11000, 'LEADING')
                """);
        jdbcTemplate.update("""
                INSERT INTO wallets (id, user_id, point)
                VALUES
                    (1, 1, 100000),
                    (2, 2, 100000),
                    (3, 3, 0)
                """);
        jdbcTemplate.update("""
                INSERT INTO wallet_holds (id, wallet_id, auction_id, amount, status)
                VALUES
                    (1, 1, 1, 11000, 'HELD'),
                    (2, 2, 2, 11000, 'HELD')
                """);
    }

    private void deleteFixtures() {
        jdbcTemplate.update("DELETE FROM orders WHERE auction_id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM point_records WHERE auction_id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM wallet_holds WHERE auction_id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (1, 2, 3)");
        jdbcTemplate.update("DELETE FROM bids WHERE auction_id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM auctions WHERE id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM card_metadata WHERE id IN (1, 2)");
        jdbcTemplate.update("DELETE FROM card_sets WHERE id = 1");
        jdbcTemplate.update("DELETE FROM users WHERE id IN (1, 2, 3)");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    static class ConcurrentWalletCalls {
        private final ConcurrentHashMap<Long, AtomicInteger> callCounts = new ConcurrentHashMap<>();
        private final Set<Integer> firstTargets = ConcurrentHashMap.newKeySet();
        private volatile CountDownLatch firstCallsReady = new CountDownLatch(2);
        private volatile boolean enabled = true;

        void reset() {
            callCounts.clear();
            firstTargets.clear();
            firstCallsReady = new CountDownLatch(2);
            enabled = true;
        }

        void disable() {
            enabled = false;
        }

        Set<Integer> firstLockTargets() {
            return Set.copyOf(firstTargets);
        }

        <T> T coordinate(Integer userId, Supplier<T> operation) {
            if (!enabled) {
                return operation.get();
            }
            AtomicInteger calls = callCounts.computeIfAbsent(
                    Thread.currentThread().threadId(), ignored -> new AtomicInteger()
            );
            boolean firstCall = calls.incrementAndGet() == 1;
            if (firstCall) {
                firstTargets.add(userId);
                firstCallsReady.countDown();
                await(firstCallsReady);
            }
            return operation.get();
        }
    }

    @TestConfiguration
    static class WalletTestConfiguration {
        @Bean
        @Primary
        CoordinatedWalletService coordinatedWalletService(
                WalletRepository walletRepository,
                PointRecordRepository pointRecordRepository,
                WalletHoldRepository walletHoldRepository,
                WalletMetrics walletMetrics,
                Clock clock
        ) {
            return new CoordinatedWalletService(
                    walletRepository,
                    pointRecordRepository,
                    walletHoldRepository,
                    walletMetrics,
                    clock
            );
        }
    }

    static class CoordinatedWalletService extends WalletService {
        private final ConcurrentWalletCalls coordinatedWalletCalls = new ConcurrentWalletCalls();

        CoordinatedWalletService(
                WalletRepository walletRepository,
                PointRecordRepository pointRecordRepository,
                WalletHoldRepository walletHoldRepository,
                WalletMetrics walletMetrics,
                Clock clock
        ) {
            super(walletRepository, pointRecordRepository, walletHoldRepository, walletMetrics, clock);
        }

        void resetCoordination() {
            coordinatedWalletCalls.reset();
        }

        void disableCoordination() {
            coordinatedWalletCalls.disable();
        }

        ConcurrentWalletCalls coordinatedWalletCalls() {
            return coordinatedWalletCalls;
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public com.dbidding.wallet.dto.WalletBalanceResponse hold(
                Integer userId,
                Integer auctionId,
                long totalAmount
        ) {
            return coordinatedWalletCalls.coordinate(
                    userId,
                    () -> super.hold(userId, auctionId, totalAmount)
            );
        }

        @Override
        @Transactional(propagation = Propagation.MANDATORY)
        public com.dbidding.wallet.dto.WalletBalanceResponse release(
                Integer userId,
                Integer auctionId
        ) {
            return coordinatedWalletCalls.coordinate(
                    userId,
                    () -> super.release(userId, auctionId)
            );
        }
    }
}
