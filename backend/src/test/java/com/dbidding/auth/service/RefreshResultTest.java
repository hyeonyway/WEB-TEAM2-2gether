package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.dbidding.auth.dto.RefreshResponse;

class RefreshResultTest {

	@Test
	void 문자열에_access와_refresh_token을_노출하지_않는다() {
		RefreshResult result = new RefreshResult(
			new RefreshResponse("raw-access-token"),
			"raw-refresh-token"
		);

		assertThat(result.toString())
			.doesNotContain("raw-access-token")
			.doesNotContain("raw-refresh-token");
	}
}
