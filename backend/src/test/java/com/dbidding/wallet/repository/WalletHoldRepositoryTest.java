package com.dbidding.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.domain.WalletHold;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.sql.init.mode=always")
class WalletHoldRepositoryTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private WalletHoldRepository walletHoldRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Integer walletId;
	private Integer auctionId;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("""
			INSERT INTO users (
			    id, email, nickname, role, status, encrypted_password, salt
			) VALUES (
			    1, 'wallet-hold@test.local', 'wallet-hold', 'USER', 'ACTIVE',
			    REPEAT('a', 64), REPEAT('b', 32)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO card_sets (id, name, code)
			VALUES (1, 'Wallet Hold 테스트 세트', 'WALLET-HOLD')
			""");
		jdbcTemplate.update("""
			INSERT INTO card_metadata (id, card_set_id, name)
			VALUES (1, 1, 'Wallet Hold 테스트 카드')
			""");
		jdbcTemplate.update("""
			INSERT INTO auctions (
			    id, user_id, item_id, auction_name, description,
			    start_price, current_price, buy_now_price, delivery_fee,
			    status, open_time, estimated_close_time, close_time,
			    bid_count, bid_price_unit, is_hyped, version
			) VALUES (
			    1, 1, 1, 'Wallet Hold 테스트 경매', 'Wallet Hold Repository 테스트',
			    1000, 1000, 10000, 3000,
			    'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    0, 1000, FALSE, 1
			)
			""");
		walletId = walletRepository.saveAndFlush(Wallet.open(1)).getId();
		auctionId = 1;
	}

	@Test
	void 같은_wallet과_auction의_가장_최근_hold를_조회한다() {
		WalletHold released = walletHoldRepository.saveAndFlush(
			WalletHold.held(walletId, auctionId, 11_000L)
		);
		released.release(Instant.parse("2026-07-29T00:00:00Z"));
		walletHoldRepository.flush();
		WalletHold held = walletHoldRepository.saveAndFlush(
			WalletHold.held(walletId, auctionId, 16_000L)
		);

		assertThat(walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			walletId,
			auctionId
		)).contains(held);
	}
}
