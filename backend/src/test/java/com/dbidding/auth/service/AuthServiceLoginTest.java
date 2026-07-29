package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.auth.dto.LoginRequest;
import com.dbidding.auth.dto.LoginResponse;
import com.dbidding.auth.exception.InvalidCredentialsException;
import com.dbidding.auth.password.PasswordHasher;
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.auth.repository.AuthenticationRepository;
import com.dbidding.auth.token.IssuedTokens;
import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.auth.token.RefreshTokenHasher;

@ExtendWith(MockitoExtension.class)
class AuthServiceLoginTest {

	private static final LoginRequest REQUEST = new LoginRequest(
		"collector@example.com",
		"Password123!"
	);
	private static final Instant ACCESS_EXPIRES_AT = Instant.parse("2026-07-28T09:30:00Z");
	private static final Instant REFRESH_EXPIRES_AT = Instant.parse("2026-08-04T09:00:00Z");
	private static final IssuedTokens TOKENS = new IssuedTokens(
		"access-token",
		"refresh-token",
		ACCESS_EXPIRES_AT,
		REFRESH_EXPIRES_AT
	);
	private static final String REFRESH_TOKEN_HASH = "c".repeat(64);

	@Mock
	private UserAccountPort userAccountPort;

	@Mock
	private WalletProvisioningPort walletProvisioningPort;

	@Mock
	private PasswordHasher passwordHasher;

	@Mock
	private AuthenticationRepository authenticationRepository;

	@Mock
	private JwtTokenProvider jwtTokenProvider;

	@Mock
	private RefreshTokenHasher refreshTokenHasher;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(
			userAccountPort,
			walletProvisioningPort,
			passwordHasher,
			authenticationRepository,
			jwtTokenProvider,
			refreshTokenHasher
		);
	}

	@Test
	void 존재하지_않는_이메일이면_동일한_인증_실패로_처리한다() {
		given(userAccountPort.findByEmail(REQUEST.email())).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.login(REQUEST))
			.isInstanceOf(InvalidCredentialsException.class);

		then(passwordHasher).should().matches(
			eq(REQUEST.password()),
			anyString(),
			anyString()
		);
		then(jwtTokenProvider).shouldHaveNoInteractions();
		then(authenticationRepository).shouldHaveNoInteractions();
	}

	@Test
	void 비밀번호가_틀리면_동일한_인증_실패로_처리한다() {
		UserAccount user = userAccount("ACTIVE");
		given(userAccountPort.findByEmail(REQUEST.email())).willReturn(Optional.of(user));
		given(passwordHasher.matches(
			REQUEST.password(),
			user.salt(),
			user.encryptedPassword()
		)).willReturn(false);

		assertThatThrownBy(() -> authService.login(REQUEST))
			.isInstanceOf(InvalidCredentialsException.class);

		then(jwtTokenProvider).shouldHaveNoInteractions();
		then(authenticationRepository).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@ValueSource(strings = {"SUSPENDED", "WITHDRAWN"})
	void 비활성_계정이면_토큰을_발급하지_않는다(String status) {
		UserAccount user = userAccount(status);
		given(userAccountPort.findByEmail(REQUEST.email())).willReturn(Optional.of(user));
		given(passwordHasher.matches(
			REQUEST.password(),
			user.salt(),
			user.encryptedPassword()
		)).willReturn(true);

		assertThatThrownBy(() -> authService.login(REQUEST))
			.isInstanceOf(InvalidCredentialsException.class);

		then(jwtTokenProvider).shouldHaveNoInteractions();
		then(authenticationRepository).shouldHaveNoInteractions();
	}

	@Test
	void 로그인하면_refresh_hash를_저장하고_access를_반환한다() {
		UserAccount user = userAccount("ACTIVE");
		givenSuccessfulCredentialValidation(user);
		given(jwtTokenProvider.issue(
			eq(user.id()),
			eq(UserAccountRole.USER),
			any(Instant.class)
		)).willReturn(TOKENS);
		given(refreshTokenHasher.hash(TOKENS.refreshToken())).willReturn(REFRESH_TOKEN_HASH);

		LoginResult result = authService.login(REQUEST);

		assertThat(result).isEqualTo(new LoginResult(
			new LoginResponse(TOKENS.accessToken()),
			TOKENS.refreshToken()
		));
		then(authenticationRepository).should().upsertRefreshTokenHash(
			user.id(),
			REFRESH_TOKEN_HASH
		);
	}

	private void givenSuccessfulCredentialValidation(UserAccount user) {
		given(userAccountPort.findByEmail(REQUEST.email())).willReturn(Optional.of(user));
		given(passwordHasher.matches(
			REQUEST.password(),
			user.salt(),
			user.encryptedPassword()
		)).willReturn(true);
	}

	private UserAccount userAccount(String status) {
		return new UserAccount(
			1,
			REQUEST.email(),
			"collector",
			UserAccountRole.USER,
			status,
			"encrypted-password",
			"salt"
		);
	}
}
