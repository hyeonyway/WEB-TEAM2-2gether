package com.dbidding.account.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AccountTest {

	@Test
	void 신규_계정은_USER_ACTIVE_상태로_생성된다() {
		Account account = Account.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		);

		assertThat(account.getEmail()).isEqualTo("collector@example.com");
		assertThat(account.getNickname()).isEqualTo("collector");
		assertThat(account.getEncryptedPassword()).isEqualTo("a".repeat(64));
		assertThat(account.getSalt()).isEqualTo("b".repeat(32));
		assertThat(account.getRole()).isEqualTo(AccountRole.USER);
		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}
}
