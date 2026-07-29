package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dbidding.auth.dto.LoginResponse;

class LoginResultTest {

	@Test
	void 문자열_표현에_refresh_token_원문을_노출하지_않는다() {
		LoginResult result = new LoginResult(
			new LoginResponse("access-token"),
			"raw-refresh-token"
		);

		assertThat(result.toString()).doesNotContain("raw-refresh-token");
	}
}
