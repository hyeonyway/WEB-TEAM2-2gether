package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.dbidding.auction.adapter.AuctionWalletAdapter;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.auction.port.AuctionEventPort;
import com.dbidding.auction.port.WalletPort;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
	"statistic.scheduler.enabled=false",
	"spring.sql.init.mode=always",
	"spring.jpa.hibernate.ddl-auto=validate"
})
@Import(AuctionBidWalletLockOrderConcurrencyTest.WalletPortTestConfiguration.class)
class AuctionBidWalletLockOrderConcurrencyTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private AuctionCommandService auctionCommandService;

	@Autowired
	private CoordinatedWalletPort walletPort;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@MockitoBean
	private AuctionCardStatisticPort auctionCardStatisticPort;

	@MockitoBean
	private AuctionEventPort auctionEventPort;

	private ExecutorService executor;

	@BeforeEach
	void setUp() {
		executor = Executors.newFixedThreadPool(2);
		insertFixtures();
		walletPort.coordinateFirstWalletLocks();
	}

	@AfterEach
	void tearDown() throws InterruptedException {
		executor.shutdownNow();
		executor.awaitTermination(5, TimeUnit.SECONDS);
		deleteFixtures();
	}

	@RepeatedTest(2)
	void 서로_다른_경매의_교차_outbid는_지갑_데드락_없이_모두_성공한다() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		Callable<BidResponses.BidResult> bidderTwoOutbidsOne = participate(
			2,
			1,
			"bidder-two-auction-one",
			ready,
			start
		);
		Callable<BidResponses.BidResult> bidderOneOutbidsTwo = participate(
			1,
			2,
			"bidder-one-auction-two",
			ready,
			start
		);

		Future<BidResponses.BidResult> first = executor.submit(bidderTwoOutbidsOne);
		Future<BidResponses.BidResult> second = executor.submit(bidderOneOutbidsTwo);
		assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
		start.countDown();

		assertThat(first.get(10, TimeUnit.SECONDS).bid().amount()).isEqualTo(12_000L);
		assertThat(second.get(10, TimeUnit.SECONDS).bid().amount()).isEqualTo(12_000L);
		assertThat(walletPort.firstLockTargets()).containsExactly(1);
	}

	private Callable<BidResponses.BidResult> participate(
		Integer bidderId,
		Integer auctionId,
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
				new BidCreateRequest(12_000L),
				idempotencyKey
			);
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
			    bid_count, bid_price_unit, is_hyped, version
			) VALUES
			    (1, 3, 1, '교차 경매 1', '교차 경매 테스트 1',
			     10000, 11000, 100000, 0, 'OPEN', UTC_TIMESTAMP(6),
			     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
			     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 1, 1000, FALSE, 1),
			    (2, 3, 2, '교차 경매 2', '교차 경매 테스트 2',
			     10000, 11000, 100000, 0, 'OPEN', UTC_TIMESTAMP(6),
			     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR),
			     DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 HOUR), 1, 1000, FALSE, 1)
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
			    (2, 2, 100000)
			""");
		jdbcTemplate.update("""
			INSERT INTO wallet_holds (id, wallet_id, auction_id, amount, status)
			VALUES
			    (1, 1, 1, 11000, 'HELD'),
			    (2, 2, 2, 11000, 'HELD')
			""");
	}

	private void deleteFixtures() {
		jdbcTemplate.update("DELETE FROM wallet_holds WHERE auction_id IN (1, 2)");
		jdbcTemplate.update("DELETE FROM wallets WHERE user_id IN (1, 2)");
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

	@TestConfiguration
	static class WalletPortTestConfiguration {

		@Bean
		@Primary
		CoordinatedWalletPort coordinatedWalletPort(AuctionWalletAdapter delegate) {
			return new CoordinatedWalletPort(delegate);
		}
	}

	static class CoordinatedWalletPort implements WalletPort {
		private final WalletPort delegate;
		private final ConcurrentHashMap<Long, Integer> callCounts = new ConcurrentHashMap<>();
		private final Set<Integer> firstTargets = ConcurrentHashMap.newKeySet();
		private volatile CountDownLatch firstCallsReady;
		private volatile CountDownLatch distinctFirstLocksAcquired;

		CoordinatedWalletPort(WalletPort delegate) {
			this.delegate = delegate;
		}

		void coordinateFirstWalletLocks() {
			callCounts.clear();
			firstTargets.clear();
			firstCallsReady = new CountDownLatch(2);
			distinctFirstLocksAcquired = new CountDownLatch(2);
		}

		Set<Integer> firstLockTargets() {
			return Set.copyOf(firstTargets);
		}

		@Override
		public WalletSnapshot getWallet(Integer userId) {
			return delegate.getWallet(userId);
		}

		@Override
		public WalletSnapshot holdBidAmount(Integer userId, Integer auctionId, long amount) {
			return coordinate(userId, () -> delegate.holdBidAmount(userId, auctionId, amount));
		}

		@Override
		public WalletSnapshot releaseBidHold(Integer userId, Integer auctionId) {
			return coordinate(userId, () -> delegate.releaseBidHold(userId, auctionId));
		}

		@Override
		public WalletSnapshot confirmWinningBid(Integer userId, Integer auctionId, long amount) {
			return delegate.confirmWinningBid(userId, auctionId, amount);
		}

		private WalletSnapshot coordinate(Integer userId, Supplier<WalletSnapshot> operation) {
			long threadId = Thread.currentThread().threadId();
			boolean firstCall = callCounts.merge(threadId, 1, Integer::sum) == 1;
			if (firstCall) {
				firstTargets.add(userId);
				firstCallsReady.countDown();
				await(firstCallsReady);
			}

			WalletSnapshot result = operation.get();
			if (firstCall && firstTargets.size() > 1) {
				distinctFirstLocksAcquired.countDown();
				await(distinctFirstLocksAcquired);
			}
			return result;
		}
	}
}
