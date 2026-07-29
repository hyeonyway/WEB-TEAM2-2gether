package com.dbidding.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserTest {

	@Test
	void 신규_사용자는_USER_ACTIVE_상태로_생성된다() {
		User user = User.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		);

		assertThat(user.getEmail()).isEqualTo("collector@example.com");
		assertThat(user.getNickname()).isEqualTo("collector");
		assertThat(user.getEncryptedPassword()).isEqualTo("a".repeat(64));
		assertThat(user.getSalt()).isEqualTo("b".repeat(32));
		assertThat(user.getRole()).isEqualTo(UserRole.USER);
		assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
	}
}
