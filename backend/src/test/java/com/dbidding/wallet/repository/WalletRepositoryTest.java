package com.dbidding.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;

import com.dbidding.wallet.domain.Wallet;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class WalletRepositoryTest {

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Integer userId;

	@BeforeEach
	void setUp() {
		Number generatedId = new SimpleJdbcInsert(jdbcTemplate)
			.withTableName("users")
			.usingColumns("email", "nickname", "role", "status", "encrypted_password", "salt")
			.usingGeneratedKeyColumns("id")
			.executeAndReturnKey(new MapSqlParameterSource()
				.addValue("email", "wallet@example.com")
				.addValue("nickname", "wallet-user")
				.addValue("role", "USER")
				.addValue("status", "ACTIVE")
				.addValue("encrypted_password", "a".repeat(64))
				.addValue("salt", "b".repeat(32)));
		userId = generatedId.intValue();
	}

	@Test
	void 사용자_ID로_초기_잔액_0인_지갑을_조회한다() {
		walletRepository.saveAndFlush(Wallet.open(userId));

		assertThat(walletRepository.existsByUserId(userId)).isTrue();
		assertThat(walletRepository.findByUserId(userId))
			.isPresent()
			.get()
			.extracting(Wallet::getPoint)
			.isEqualTo(0L);
	}

	@Test
	void 한_사용자에게_지갑을_두_개_저장할_수_없다() {
		walletRepository.saveAndFlush(Wallet.open(userId));

		assertThatThrownBy(() -> walletRepository.saveAndFlush(Wallet.open(userId)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}
}
