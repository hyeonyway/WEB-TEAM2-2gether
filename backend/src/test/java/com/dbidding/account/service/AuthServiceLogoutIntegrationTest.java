package com.dbidding.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import com.dbidding.account.authentication.jwt.Authentication;
import com.dbidding.account.authentication.jwt.AuthenticationRepository;
import com.dbidding.account.authentication.jwt.IssuedTokens;
import com.dbidding.account.authentication.jwt.JwtAuthenticationStrategy;
import com.dbidding.account.authentication.jwt.JwtRefreshService;
import com.dbidding.account.authentication.jwt.JwtTokenProvider;
import com.dbidding.account.authentication.jwt.RefreshTokenHasher;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.exception.InvalidRefreshTokenException;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.support.AccountMySqlIntegrationTest;

import jakarta.servlet.http.Cookie;

class AuthServiceLogoutIntegrationTest extends AccountMySqlIntegrationTest {

	@Autowired
	private JwtAuthenticationStrategy jwtAuthenticationStrategy;

	@Autowired
	private JwtRefreshService jwtRefreshService;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private RefreshTokenHasher refreshTokenHasher;

	private String refreshToken;

	@BeforeEach
	void setUp() {
		Account account = accountRepository.saveAndFlush(Account.create(
			"logout-integration@example.com",
			"logout-integration",
			"a".repeat(64),
			"b".repeat(32)
		));
		IssuedTokens tokens = jwtTokenProvider.issue(account.getId(), AccountRole.USER, Instant.now());
		refreshToken = tokens.refreshToken();
		authenticationRepository.saveAndFlush(Authentication.issue(
			account.getId(),
			refreshTokenHasher.hash(refreshToken)
		));
	}

	@Test
	void 로그아웃하면_기존_refresh_token으로_재발급할_수_없다() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setCookies(new Cookie("refreshToken", refreshToken));
		jwtAuthenticationStrategy.terminate(request);

		assertThat(authenticationRepository.findByRefreshTokenHash(
			refreshTokenHasher.hash(refreshToken)
		)).isEmpty();
		assertThatThrownBy(() -> jwtRefreshService.refresh(refreshToken))
			.isInstanceOf(InvalidRefreshTokenException.class);
	}
}
