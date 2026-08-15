package com.dbidding.account.warning;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountStatus;

class AccountStatusTransitionTest {

	@Test
	void 정지된_계정을_다시_활성화한다() {
		Account account = Account.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		);

		account.suspend();
		account.activate();

		assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
	}
}
