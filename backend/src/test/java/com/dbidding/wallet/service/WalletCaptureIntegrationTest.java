package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
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

import com.dbidding.wallet.domain.HoldStatus;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
	"statistic.scheduler.enabled=false",
	"spring.sql.init.mode=always",
	"spring.jpa.hibernate.ddl-auto=validate"
})
class WalletCaptureIntegrationTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private WalletService walletService;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private WalletHoldRepository walletHoldRepository;

	@Autowired
	private PointRecordRepository pointRecordRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private ExecutorService executor;

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
			    1, 'wallet-capture@test.local', 'wallet-capture',
			    'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO card_sets (id, name, code)
			VALUES (1, 'Wallet Capture 세트', 'WALLET-CAPTURE')
			""");
		jdbcTemplate.update("""
			INSERT INTO card_metadata (id, card_set_id, name)
			VALUES (1, 1, 'Wallet Capture 카드')
			""");
		jdbcTemplate.update("""
			INSERT INTO auctions (
			    id, user_id, item_id, auction_name, description,
			    start_price, current_price, buy_now_price, delivery_fee,
			    status, open_time, estimated_close_time, close_time,
			    bid_count, bid_price_unit, is_hyped
			) VALUES (
			    1, 1, 1, 'Wallet Capture 경매', 'Wallet Capture 통합 테스트',
			    1000, 1000, 10000, 3000,
			    'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    0, 1000, FALSE
			)
			""");
		Wallet wallet = Wallet.open(1);
		wallet.credit(20_000L);
		walletRepository.saveAndFlush(wallet);
		walletHoldRepository.saveAndFlush(WalletHold.held(wallet.getId(), 1, 16_000L));
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(5, TimeUnit.SECONDS);
	}

	@Test
	void 동시_낙찰_확정은_잔액과_원장을_한_번만_변경한다() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<WalletBalanceResponse> capture = () -> {
			ready.countDown();
			assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
			return transactionTemplate.execute(status ->
				walletService.capture(1, 1, 16_000L)
			);
		};
		List<Future<WalletBalanceResponse>> futures = List.of(
			executor.submit(capture),
			executor.submit(capture)
		);

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		WalletBalanceResponse first = futures.get(0).get(10, TimeUnit.SECONDS);
		WalletBalanceResponse second = futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(first.availableBalance()).isEqualTo(4_000L);
		assertThat(second.availableBalance()).isEqualTo(4_000L);
		assertThat(walletRepository.findByUserId(1))
			.isPresent()
			.get()
			.extracting(Wallet::getPoint)
			.isEqualTo(4_000L);
		WalletHold latestHold = transactionTemplate.execute(status ->
			walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
				walletRepository.findByUserId(1).orElseThrow().getId(),
				1
			).orElseThrow()
		);
		assertThat(latestHold)
			.extracting(WalletHold::getStatus)
			.isEqualTo(HoldStatus.CAPTURED);
		assertThat(pointRecordRepository.count()).isEqualTo(1L);
	}
}
