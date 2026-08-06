package com.dbidding.account.authentication.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.domain.AccountRole;

class SessionAuthenticationStrategyTest {

	private static final Instant NOW = Instant.parse("2026-08-06T01:30:00Z");

	private SessionAuthenticationStrategy strategy;

	@BeforeEach
	void setUp() {
		SessionProperties properties = new SessionProperties(SessionStore.MEMORY, "SESSION", false);
		strategy = new SessionAuthenticationStrategy(properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void 인증을_수립하면_최소_인증_정보만_세션에_저장한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();

		ResponseEntity<?> response = strategy.establish(
			new AuthenticatedAccount(7, AccountRole.USER),
			request
		);

		MockHttpSession session = (MockHttpSession)request.getSession(false);
		assertThat(response.getStatusCode().value()).isEqualTo(204);
		assertThat(session).isNotNull();
		assertThat(Collections.list(session.getAttributeNames()))
			.containsExactlyInAnyOrder(
				SessionPrincipal.USER_ID_ATTRIBUTE,
				SessionPrincipal.ROLE_ATTRIBUTE,
				SessionPrincipal.AUTHENTICATED_AT_ATTRIBUTE
			);
		assertThat(session.getAttribute(SessionPrincipal.USER_ID_ATTRIBUTE)).isEqualTo(7);
		assertThat(session.getAttribute(SessionPrincipal.ROLE_ATTRIBUTE)).isEqualTo("USER");
		assertThat(session.getAttribute(SessionPrincipal.AUTHENTICATED_AT_ATTRIBUTE))
			.isEqualTo(NOW.getEpochSecond());
	}

	@Test
	void 기존_익명_세션이_있으면_로그인할_때_ID를_교체한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession anonymousSession = (MockHttpSession)request.getSession(true);
		String previousSessionId = anonymousSession.getId();

		strategy.establish(new AuthenticatedAccount(7, AccountRole.USER), request);

		assertThat(request.getSession(false).getId()).isNotEqualTo(previousSessionId);
	}

	@Test
	void 인증을_종료하면_세션과_쿠키를_폐기한다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpSession session = (MockHttpSession)request.getSession(true);

		ResponseEntity<Void> response = strategy.terminate(request);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		assertThat(session.isInvalid()).isTrue();
		assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
			.isEqualTo("SESSION=; Path=/; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; HttpOnly; SameSite=Lax");
	}

	@Test
	void 세션이_없어도_로그아웃은_멱등하게_성공한다() {
		ResponseEntity<Void> response = strategy.terminate(new MockHttpServletRequest());

		assertThat(response.getStatusCode().value()).isEqualTo(204);
	}
}
