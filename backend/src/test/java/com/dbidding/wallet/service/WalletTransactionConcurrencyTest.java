package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
	"statistics.scheduler.enabled=false",
	"spring.sql.init.mode=always",
	"spring.jpa.hibernate.ddl-auto=validate"
})
class WalletTransactionConcurrencyTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private WalletTransactionService walletTransactionService;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private PointRecordRepository pointRecordRepository;

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
			INSERT INTO users (
			    id, email, nickname, role, status, encrypted_password, salt
			) VALUES (
			    1, 'wallet-concurrency@test.local', 'wallet-concurrency',
			    'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO card_sets (id, name, code)
			VALUES (1, 'Wallet 동시성 세트', 'WALLET-CONCURRENCY')
			""");
		jdbcTemplate.update("""
			INSERT INTO card_metadata (id, card_set_id, name)
			VALUES (1, 1, 'Wallet 동시성 카드')
			""");
		jdbcTemplate.update("""
			INSERT INTO auctions (
			    id, user_id, item_id, auction_name, description,
			    start_price, current_price, buy_now_price, delivery_fee,
			    status, open_time, estimated_close_time, close_time,
			    bid_count, bid_price_unit, is_hyped, version
			) VALUES (
			    1, 1, 1, 'Wallet 동시성 경매', 'Wallet 동시성 테스트',
			    1000, 1000, 10000, 3000,
			    'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    0, 1000, FALSE, 1
			)
			""");
		walletId = walletRepository.saveAndFlush(Wallet.open(1)).getId();
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(5, TimeUnit.SECONDS);
	}

	@Test
	void 동일한_idempotency_key의_동시_충전은_잔액과_원장을_한_번만_변경한다() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<WalletTransactionResponse> charge = () -> {
			ready.countDown();
			await(start);
			return walletTransactionService.charge(1, 10_000L, "same-key");
		};
		List<Future<WalletTransactionResponse>> futures = List.of(
			executor.submit(charge),
			executor.submit(charge)
		);

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		WalletTransactionResponse first = futures.get(0).get(10, TimeUnit.SECONDS);
		WalletTransactionResponse second = futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(first.transactionId()).isEqualTo(second.transactionId());
		assertThat(first.balance()).isEqualTo(10_000L);
		assertThat(second.balance()).isEqualTo(10_000L);
		assertThat(walletRepository.findByUserId(1))
			.isPresent()
			.get()
			.extracting(Wallet::getPoint)
			.isEqualTo(10_000L);
		assertThat(pointRecordRepository.count()).isEqualTo(1L);
	}

	@Test
	void HELD_생성과_환불이_겹쳐도_가용_잔액을_초과해_환불하지_않는다() throws Exception {
		jdbcTemplate.update("UPDATE wallets SET point = 10000 WHERE id = ?", walletId);
		CountDownLatch holdCreated = new CountDownLatch(1);
		CountDownLatch allowHoldCommit = new CountDownLatch(1);
		Future<?> holdFuture = executor.submit(() ->
			transactionTemplate.executeWithoutResult(status -> {
				walletRepository.findByUserIdForUpdate(1).orElseThrow();
				jdbcTemplate.update("""
					INSERT INTO wallet_holds(wallet_id, auction_id, amount, status)
					VALUES (?, 1, 3000, 'HELD')
					""", walletId);
				holdCreated.countDown();
				await(allowHoldCommit);
			})
		);
		assertThat(holdCreated.await(5, TimeUnit.SECONDS)).isTrue();

		CountDownLatch refundStarted = new CountDownLatch(1);
		Future<WalletTransactionResponse> refundFuture = executor.submit(() -> {
			refundStarted.countDown();
			return walletTransactionService.refund(1, 7_001L, "refund-key");
		});
		assertThat(refundStarted.await(5, TimeUnit.SECONDS)).isTrue();
		allowHoldCommit.countDown();
		holdFuture.get(10, TimeUnit.SECONDS);

		assertThatThrownBy(() -> get(refundFuture))
			.isInstanceOf(InsufficientAvailableBalanceException.class);
		assertThat(walletRepository.findByUserId(1))
			.isPresent()
			.get()
			.extracting(Wallet::getPoint)
			.isEqualTo(10_000L);
		assertThat(pointRecordRepository.count()).isZero();
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("동시성 테스트 대기 시간이 초과되었습니다.");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("동시성 테스트가 중단되었습니다.", exception);
		}
	}

	private WalletTransactionResponse get(Future<WalletTransactionResponse> future) throws Throwable {
		try {
			return future.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
		} catch (ExecutionException exception) {
			throw exception.getCause();
		}
	}
}
