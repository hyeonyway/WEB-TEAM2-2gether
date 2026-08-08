package com.dbidding.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.Wallet;

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.sql.init.mode=always")
class WalletLedgerRepositoryTest {

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private PointRecordRepository pointRecordRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Integer userId;
	private Integer auctionId;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("""
			INSERT INTO users (
			    id, email, nickname, role, status, encrypted_password, salt
			) VALUES (
			    1, 'wallet-ledger@test.local', 'wallet-ledger', 'USER', 'ACTIVE',
			    REPEAT('a', 64), REPEAT('b', 32)
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO card_sets (id, name, code)
			VALUES (1, 'Wallet 테스트 세트', 'WALLET-TEST')
			""");
		jdbcTemplate.update("""
			INSERT INTO card_metadata (id, card_set_id, name)
			VALUES (1, 1, 'Wallet 테스트 카드')
			""");
		jdbcTemplate.update("""
			INSERT INTO auctions (
			    id, user_id, item_id, auction_name, description,
			    start_price, current_price, buy_now_price, delivery_fee,
			    status, open_time, estimated_close_time, close_time,
			    bid_count, bid_price_unit, is_hyped
			) VALUES (
			    1, 1, 1, 'Wallet 테스트 경매', 'Wallet 원장 테스트',
			    1000, 1000, 10000, 3000,
			    'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    0, 1000, FALSE
			)
			""");
		userId = 1;
		auctionId = 1;
	}

	@Test
	void HELD_금액만_합산하고_idempotency_key로_원장을_조회한다() {
		Wallet wallet = walletRepository.saveAndFlush(Wallet.open(userId));
		jdbcTemplate.update("""
			INSERT INTO wallet_holds(wallet_id, auction_id, amount, status)
			VALUES (?, ?, 3000, 'HELD'), (?, ?, 9000, 'RELEASED')
			""", wallet.getId(), auctionId, wallet.getId(), auctionId);
		PointRecord record = pointRecordRepository.saveAndFlush(
			PointRecord.charge(wallet.getId(), 10_000L, 10_000L, "charge-key")
		);

		assertThat(walletRepository.sumHeldAmount(wallet.getId())).isEqualTo(3_000L);
		assertThat(pointRecordRepository.findByWalletIdAndIdempotencyKey(
			wallet.getId(),
			"charge-key"
		)).contains(record);
	}

	@Test
	void 사용자_ID로_wallet_row를_쓰기_잠금_조회한다() {
		Wallet wallet = walletRepository.saveAndFlush(Wallet.open(userId));

		assertThat(walletRepository.findByUserIdForUpdate(userId)).contains(wallet);
	}
}
