package com.dbidding.account.warning;

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

@Testcontainers(disabledWithoutDocker = true)
@DataJpaTest(properties = "spring.sql.init.mode=always")
class UserWarningRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

	@Container
	@ServiceConnection
	static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
		.withDatabaseName("dbidding");

	@Autowired
	private UserWarningRepository userWarningRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		jdbcTemplate.update("""
			INSERT INTO users (id, email, nickname, role, status, encrypted_password, salt)
			VALUES (1, 'warning-user@test.local', 'warning-user', 'USER', 'ACTIVE', REPEAT('a', 64), REPEAT('b', 32))
			""");
		jdbcTemplate.update("""
			INSERT INTO card_sets (id, name, code)
			VALUES (1, 'Warning 테스트 세트', 'WARNING')
			""");
		jdbcTemplate.update("""
			INSERT INTO card_metadata (id, card_set_id, name)
			VALUES (1, 1, 'Warning 테스트 카드')
			""");
		jdbcTemplate.update("""
			INSERT INTO auctions (
			    id, user_id, item_id, auction_name, description, start_price, current_price,
			    buy_now_price, delivery_fee, status, open_time, estimated_close_time, close_time,
			    bid_count, bid_price_unit, is_hyped
			) VALUES (
			    1, 1, 1, 'Warning 테스트 경매', 'Warning Repository 테스트', 1000, 1000,
			    10000, 3000, 'OPEN', NOW(6), DATE_ADD(NOW(6), INTERVAL 1 HOUR),
			    DATE_ADD(NOW(6), INTERVAL 1 HOUR), 0, 1000, FALSE
			)
			""");
		jdbcTemplate.update("""
			INSERT INTO orders (id, auction_id, buyer_id, seller_id, card_name, price, status)
			VALUES (1, 1, 1, 1, 'Warning 테스트 카드', 1000, 'CANCELLED')
			""");
	}

	@Test
	void 만료되지_않은_경고만_활성_경고로_센다() {
		userWarningRepository.save(UserWarning.issued(1, 1, UserWarningReason.BUYER_CANCELLED, NOW.minusSeconds(1), NOW.plusSeconds(1)));
		userWarningRepository.save(UserWarning.issued(1, 1, UserWarningReason.SELLER_CANCELLED, NOW.minusSeconds(2), NOW));
		userWarningRepository.flush();

		assertThat(userWarningRepository.countActiveByUserId(1, NOW)).isEqualTo(1);
	}

	@Test
	void 주문과_사유가_같은_경고의_존재를_조회한다() {
		userWarningRepository.saveAndFlush(UserWarning.issued(
			1,
			1,
			UserWarningReason.BUYER_CANCELLED,
			NOW,
			NOW.plusSeconds(60)
		));

		assertThat(userWarningRepository.existsByOrderIdAndReason(1, UserWarningReason.BUYER_CANCELLED)).isTrue();
		assertThat(userWarningRepository.existsByOrderIdAndReason(1, UserWarningReason.SELLER_CANCELLED)).isFalse();
	}
}
