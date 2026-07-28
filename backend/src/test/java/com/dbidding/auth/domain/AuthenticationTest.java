package com.dbidding.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthenticationTest {

	@Test
	void refresh_token_hash를_교체한다() {
		Authentication authentication = Authentication.issue(1, "a".repeat(64));

		authentication.rotate("b".repeat(64));

		assertThat(authentication.getRefreshTokenHash()).isEqualTo("b".repeat(64));
	}
}
