package com.dbidding.account.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;

import com.dbidding.account.domain.Account;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTest {

	@Autowired
	private AccountRepository accountRepository;

	@BeforeEach
	void setUp() {
		accountRepository.saveAndFlush(account(
			"collector@example.com",
			"collector"
		));
	}

	@Test
	void 이메일과_닉네임의_중복을_조회한다() {
		assertThat(accountRepository.existsByEmail("collector@example.com")).isTrue();
		assertThat(accountRepository.existsByNickname("collector")).isTrue();
		assertThat(accountRepository.existsByEmail("other@example.com")).isFalse();
		assertThat(accountRepository.existsByNickname("other")).isFalse();
	}

	@Test
	void 이메일로_계정을_조회한다() {
		assertThat(accountRepository.findByEmail("collector@example.com"))
			.isPresent()
			.get()
			.extracting(Account::getNickname)
			.isEqualTo("collector");
	}

	@Test
	void 동일한_이메일은_DB_UNIQUE_제약으로_거절한다() {
		Account duplicate = account("collector@example.com", "other");

		assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 동일한_닉네임은_DB_UNIQUE_제약으로_거절한다() {
		Account duplicate = account("other@example.com", "collector");

		assertThatThrownBy(() -> accountRepository.saveAndFlush(duplicate))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	private Account account(String email, String nickname) {
		return Account.create(
			email,
			nickname,
			"a".repeat(64),
			"b".repeat(32)
		);
	}
}
