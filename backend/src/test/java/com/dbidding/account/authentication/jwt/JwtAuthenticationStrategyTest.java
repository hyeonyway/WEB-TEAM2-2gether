package com.dbidding.account.authentication.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.jwt.RefreshCookieFactory;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.dto.LoginResponse;
import com.dbidding.account.authentication.jwt.AuthenticationRepository;
import com.dbidding.account.authentication.jwt.IssuedTokens;
import com.dbidding.account.authentication.jwt.JwtTokenProvider;
import com.dbidding.account.authentication.jwt.RefreshTokenHasher;

import jakarta.servlet.http.Cookie;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationStrategyTest {

	@Mock
	private AuthenticationRepository authenticationRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenHasher refreshTokenHasher;

	@Mock
	private RefreshCookieFactory refreshCookieFactory;

	private JwtAuthenticationStrategy strategy;

	@BeforeEach
	void setUp() {
		strategy = new JwtAuthenticationStrategy(
			authenticationRepository,
			jwtTokenProvider,
			refreshTokenHasher,
			refreshCookieFactory
		);
	}

	@Test
	void 인증을_수립하면_access를_응답하고_refresh_hash와_cookie를_저장한다() {
		AuthenticatedAccount account = new AuthenticatedAccount(7, AccountRole.USER);
		IssuedTokens tokens = new IssuedTokens(
			"access-token",
			"refresh-token",
			Instant.parse("2026-08-03T01:30:00Z"),
			Instant.parse("2026-08-10T01:00:00Z")
		);
		ResponseCookie cookie = ResponseCookie.from("refreshToken", "refresh-token").build();
		given(jwtTokenProvider.issue(any(), any(), any())).willReturn(tokens);
		given(refreshTokenHasher.hash("refresh-token")).willReturn("c".repeat(64));
		given(refreshCookieFactory.create("refresh-token")).willReturn(cookie);

		ResponseEntity<?> response = strategy.establish(account, new MockHttpServletRequest());

		assertThat(response.getBody()).isEqualTo(new LoginResponse("access-token"));
		assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo(cookie.toString());
		then(authenticationRepository).should().upsertRefreshTokenHash(7, "c".repeat(64));
	}

	@Test
	void 인증을_종료하면_refresh_hash를_삭제하고_cookie를_만료시킨다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("refreshToken", "refresh-token"));
		ResponseCookie expired = ResponseCookie.from("refreshToken", "").maxAge(0).build();
		given(refreshTokenHasher.hash("refresh-token")).willReturn("c".repeat(64));
		given(refreshCookieFactory.expire()).willReturn(expired);

		ResponseEntity<Void> response = strategy.terminate(request);

		assertThat(response.getStatusCode().value()).isEqualTo(204);
		assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isEqualTo(expired.toString());
		then(authenticationRepository).should().deleteByRefreshTokenHash("c".repeat(64));
	}
}
