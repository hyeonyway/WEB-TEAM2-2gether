package com.dbidding.order.service;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.dbidding.notification.service.NotificationEventListener;
import com.dbidding.order.domain.Order;
import com.dbidding.order.exception.InvalidOrderStatusException;
import com.dbidding.order.repository.OrderRepository;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.repository.WalletRepository;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
	"statistic.scheduler.enabled=false",
	"spring.sql.init.mode=always",
	"spring.jpa.hibernate.ddl-auto=validate"
})
class OrderWalletSettlementConcurrencyTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private NotificationEventListener notificationEventListener;

	private ExecutorService executor;
	private Integer firstOrderId;
	private Integer secondOrderId;

	@BeforeEach
	void setUp() {
		executor = Executors.newFixedThreadPool(2);
		jdbcTemplate.update("DELETE FROM notification");
		jdbcTemplate.update("DELETE FROM point_records");
		jdbcTemplate.update("DELETE FROM wallet_holds");
		jdbcTemplate.update("DELETE FROM orders");
		jdbcTemplate.update("DELETE FROM auctions");
		jdbcTemplate.update("DELETE FROM wallets");
		jdbcTemplate.update("DELETE FROM card_metadata");
		jdbcTemplate.update("DELETE FROM card_sets");
		jdbcTemplate.update("DELETE FROM users");
		insertUser(1, "buyer-1");
		insertUser(2, "buyer-2");
		insertUser(3, "seller");
		insertAuctions();
		walletRepository.saveAndFlush(Wallet.open(1));
		walletRepository.saveAndFlush(Wallet.open(2));
		walletRepository.saveAndFlush(Wallet.open(3));
		firstOrderId = orderRepository.saveAndFlush(
			Order.pendingConfirm(1, 1, 3, "정산 동시성 카드 1", 50_000L)
		).getId();
		secondOrderId = orderRepository.saveAndFlush(
			Order.pendingConfirm(2, 2, 3, "정산 동시성 카드 2", 30_000L)
		).getId();
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(5, TimeUnit.SECONDS);
	}

	@Test
	void 같은_주문의_확정과_판매자_취소는_한_번만_잔액을_변경한다() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Order>> futures = List.of(
			executor.submit(concurrent(ready, start, () -> orderService.confirm(firstOrderId, 1))),
			executor.submit(concurrent(ready, start, () -> orderService.sellerCancel(firstOrderId, 3)))
		);

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		int successCount = 0;
		int invalidStatusCount = 0;
		for (Future<Order> future : futures) {
			try {
				future.get(10, TimeUnit.SECONDS);
				successCount++;
			} catch (ExecutionException exception) {
				assertThat(exception.getCause()).isInstanceOf(InvalidOrderStatusException.class);
				invalidStatusCount++;
			}
		}

		assertThat(successCount).isEqualTo(1);
		assertThat(invalidStatusCount).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_records", Long.class)).isEqualTo(1L);
		assertThat(jdbcTemplate.queryForObject("SELECT ABS(amount) FROM point_records", Long.class)).isEqualTo(50_000L);
	}

	@Test
	void 서로_다른_주문을_같은_판매자에게_동시_정산해도_모두_반영된다() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Order>> futures = List.of(
			executor.submit(concurrent(ready, start, () -> orderService.confirm(firstOrderId, 1))),
			executor.submit(concurrent(ready, start, () -> orderService.confirm(secondOrderId, 2)))
		);

		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();
		futures.get(0).get(10, TimeUnit.SECONDS);
		futures.get(1).get(10, TimeUnit.SECONDS);

		assertThat(walletRepository.findByUserId(3)).isPresent().get()
			.extracting(Wallet::getPoint)
			.isEqualTo(80_000L);
		assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM point_records", Long.class)).isEqualTo(2L);
		assertThat(jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM point_records WHERE transaction_type = ?",
			Long.class,
			PointTransactionType.ORDER_SETTLEMENT.name()
		)).isEqualTo(2L);
	}

	private Callable<Order> concurrent(CountDownLatch ready, CountDownLatch start, Callable<Order> action) {
		return () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("동시성 테스트 시작 대기 시간이 초과되었습니다.");
			}
			return action.call();
		};
	}

	private void insertUser(int id, String suffix) {
		jdbcTemplate.update("""
			INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
			VALUES (?, ?, ?, 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32))
			""", id, suffix + "@order-concurrency.test", suffix);
	}

	private void insertAuctions() {
		jdbcTemplate.update("INSERT INTO card_sets (id, name, code) VALUES (1, '정산 동시성 세트', 'ORDER-CONCURRENCY')");
		jdbcTemplate.update("INSERT INTO card_metadata (id, card_set_id, name) VALUES (1, 1, '정산 동시성 카드')");
		insertAuction(1, "정산 동시성 경매 1");
		insertAuction(2, "정산 동시성 경매 2");
	}

	private void insertAuction(int id, String name) {
		jdbcTemplate.update("""
			INSERT INTO auctions (
				id, user_id, item_id, auction_name, description,
				start_price, current_price, buy_now_price, delivery_fee,
				status, open_time, estimated_close_time, close_time,
				bid_count, bid_price_unit, is_hyped
			) VALUES (?, 3, 1, ?, '정산 동시성 테스트',
				1000, 1000, 10000, 3000,
				'ENDED', NOW(6), NOW(6), NOW(6), 0, 1000, FALSE)
			""", id, name);
	}
}
