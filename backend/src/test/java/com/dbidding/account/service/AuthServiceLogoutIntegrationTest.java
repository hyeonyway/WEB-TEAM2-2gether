package com.dbidding.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.dbidding.account.domain.Authentication;
import com.dbidding.account.exception.InvalidRefreshTokenException;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AuthenticationRepository;
import com.dbidding.account.token.IssuedTokens;
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.RefreshTokenHasher;
import com.dbidding.account.domain.Account;
import com.dbidding.account.repository.AccountRepository;

@SpringBootTest
class AuthServiceLogoutIntegrationTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private AccountRepository userRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private RefreshTokenHasher refreshTokenHasher;

	private String refreshToken;

	@BeforeEach
	void setUp() {
		Account user = userRepository.saveAndFlush(Account.create(
			"logout-integration@example.com",
			"logout-integration",
			"a".repeat(64),
			"b".repeat(32)
		));
		IssuedTokens tokens = jwtTokenProvider.issue(user.getId(), AccountRole.USER, Instant.now());
		refreshToken = tokens.refreshToken();
		authenticationRepository.saveAndFlush(Authentication.issue(
			user.getId(),
			refreshTokenHasher.hash(refreshToken)
		));
	}

	@AfterEach
	void cleanUp() {
		authenticationRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 로그아웃하면_기존_refresh_token으로_재발급할_수_없다() {
		authService.logout(refreshToken);

		assertThat(authenticationRepository.findByRefreshTokenHash(
			refreshTokenHasher.hash(refreshToken)
		)).isEmpty();
		assertThatThrownBy(() -> authService.refresh(refreshToken))
			.isInstanceOf(InvalidRefreshTokenException.class);
	}
}
