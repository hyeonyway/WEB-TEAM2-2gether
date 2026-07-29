package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.dbidding.auth.domain.Authentication;
import com.dbidding.auth.exception.InvalidRefreshTokenException;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.auth.repository.AuthenticationRepository;
import com.dbidding.auth.token.IssuedTokens;
import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.auth.token.RefreshTokenHasher;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;

@SpringBootTest
class AuthServiceLogoutIntegrationTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private RefreshTokenHasher refreshTokenHasher;

	private String refreshToken;

	@BeforeEach
	void setUp() {
		User user = userRepository.saveAndFlush(User.create(
			"logout-integration@example.com",
			"logout-integration",
			"a".repeat(64),
			"b".repeat(32)
		));
		IssuedTokens tokens = jwtTokenProvider.issue(user.getId(), UserAccountRole.USER, Instant.now());
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
