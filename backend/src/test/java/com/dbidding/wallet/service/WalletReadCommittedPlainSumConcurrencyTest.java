package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "statistic.scheduler.enabled=false",
        "spring.sql.init.mode=always",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.datasource.hikari.transaction-isolation=TRANSACTION_READ_COMMITTED"
})
class WalletReadCommittedPlainSumConcurrencyTest {

    private static final long POINT = 10_000L;
    private static final long HOLD_AMOUNT = 6_000L;

    @Container
    @ServiceConnection
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("dbidding");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private ExecutorService executor;
    private Integer walletId;

    @BeforeEach
    void setUp() {
        executor = Executors.newFixedThreadPool(2);
        jdbcTemplate.update("DELETE FROM point_records");
        jdbcTemplate.update("DELETE FROM wallet_holds");
        jdbcTemplate.update("DELETE FROM wallets");
        jdbcTemplate.update("DELETE FROM auctions");
        jdbcTemplate.update("DELETE FROM card_metadata");
        jdbcTemplate.update("DELETE FROM card_sets");
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("""
                INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
                VALUES (1, 'rc-wallet@test.local', 'rc-wallet', 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32))
                """);
        jdbcTemplate.update("INSERT INTO card_sets (id, name, code) VALUES (1, 'RC 지갑 세트', 'RC-WALLET')");
        jdbcTemplate.update("INSERT INTO card_metadata (id, card_set_id, name) VALUES (1, 1, 'RC 지갑 카드')");
        jdbcTemplate.update("""
                INSERT INTO auctions (
                    id, user_id, item_id, auction_name, description, start_price, current_price,
                    buy_now_price, delivery_fee, status, open_time, estimated_close_time, close_time,
                    bid_count, bid_price_unit, is_hyped
                ) VALUES
                    (1, 1, 1, 'RC 경매 1', 'RC 동시성 테스트', 1000, 1000, 10000, 0, 'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR), DATE_ADD(NOW(6), INTERVAL 1 HOUR), 0, 1000, FALSE),
                    (2, 1, 1, 'RC 경매 2', 'RC 동시성 테스트', 1000, 1000, 10000, 0, 'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR), DATE_ADD(NOW(6), INTERVAL 1 HOUR), 0, 1000, FALSE)
                """);
        jdbcTemplate.update("INSERT INTO wallets (id, user_id, point) VALUES (1, 1, ?)", POINT);
        walletId = 1;
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        executor.shutdownNow();
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @RepeatedTest(3)
    void 일반_SUM도_READ_COMMITTED에서는_선행_스냅샷과_무관하게_직전_hold를_반영한다() throws Exception {
        String isolation = transactionTemplate.execute(status -> jdbcTemplate.queryForObject(
                "SELECT @@transaction_isolation", String.class
        ));
        assertThat(isolation).isEqualTo("READ-COMMITTED");

        CountDownLatch snapshotReady = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = List.of(
                executor.submit(plainSumHold(1, snapshotReady, start)),
                executor.submit(plainSumHold(2, snapshotReady, start))
        );

        assertThat(snapshotReady.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        int success = 0;
        int insufficient = 0;
        for (Future<Long> future : futures) {
            try {
                future.get(10, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException exception) {
                assertThat(exception.getCause()).isInstanceOf(InsufficientBalance.class);
                insufficient++;
            }
        }
        assertThat(success).isEqualTo(1);
        assertThat(insufficient).isEqualTo(1);
        assertThat(heldAmount()).isEqualTo(HOLD_AMOUNT);
    }

    private Callable<Long> plainSumHold(Integer auctionId, CountDownLatch snapshotReady, CountDownLatch start) {
        return () -> transactionTemplate.execute(status -> {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bids", Long.class);
            snapshotReady.countDown();
            await(start);

            jdbcTemplate.queryForObject("SELECT id FROM wallets WHERE user_id = 1 FOR UPDATE", Integer.class);
            long held = heldAmount();
            if (POINT - held < HOLD_AMOUNT) {
                throw new InsufficientBalance();
            }
            jdbcTemplate.update("""
                    INSERT INTO wallet_holds (wallet_id, auction_id, amount, status)
                    VALUES (?, ?, ?, 'HELD')
                    """, walletId, auctionId, HOLD_AMOUNT);
            return held + HOLD_AMOUNT;
        });
    }

    private long heldAmount() {
        return jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(amount), 0)
                FROM wallet_holds
                WHERE wallet_id = ? AND status = 'HELD'
                """, Long.class, walletId);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("동시성 테스트 시작 대기 시간이 초과되었습니다.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
        }
    }

    private static final class InsufficientBalance extends RuntimeException {
    }
}
