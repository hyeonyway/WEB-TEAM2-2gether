package com.dbidding.account.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SessionCsrfTokenServiceTest {

	private final SessionCsrfTokenService service = new SessionCsrfTokenService();

	@Test
	void 세션에_예측하기_어려운_CSRF_token을_발급하고_검증한다() {
		MockHttpSession session = new MockHttpSession();

		String token = service.issue(session);

		assertThat(token).hasSizeGreaterThanOrEqualTo(40);
		assertThat(service.matches(session, token)).isTrue();
	}

	@Test
	void 다른_세션에서_발급한_CSRF_token은_검증되지_않는다() {
		MockHttpSession issuer = new MockHttpSession();
		MockHttpSession another = new MockHttpSession();
		String token = service.issue(issuer);

		assertThat(service.matches(another, token)).isFalse();
	}

	@Test
	void 누락되거나_다른_CSRF_token은_검증되지_않는다() {
		MockHttpSession session = new MockHttpSession();
		service.issue(session);

		assertThat(service.matches(session, null)).isFalse();
		assertThat(service.matches(session, "different-token")).isFalse();
	}
}
