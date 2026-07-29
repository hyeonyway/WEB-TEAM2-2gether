package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.auth.domain.Authentication;
import com.dbidding.auth.dto.RefreshResponse;
import com.dbidding.auth.exception.InvalidRefreshTokenException;
import com.dbidding.auth.password.PasswordHasher;
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.auth.repository.AuthenticationRepository;
import com.dbidding.auth.token.IssuedTokens;
import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.auth.token.RefreshTokenHasher;
import com.dbidding.auth.token.TokenClaims;
import com.dbidding.auth.token.TokenType;

@ExtendWith(MockitoExtension.class)
class AuthServiceRefreshTest {

	private static final String PRESENTED_TOKEN = "presented-refresh-token";
	private static final String PRESENTED_HASH = "a".repeat(64);
	private static final String NEXT_HASH = "b".repeat(64);
	private static final Instant ACCESS_EXPIRES_AT = Instant.parse("2026-07-29T01:30:00Z");
	private static final Instant REFRESH_EXPIRES_AT = Instant.parse("2026-08-05T01:00:00Z");
	private static final IssuedTokens NEXT_TOKENS = new IssuedTokens(
		"next-access-token",
		"next-refresh-token",
		ACCESS_EXPIRES_AT,
		REFRESH_EXPIRES_AT
	);

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
	void 유효한_refresh_token을_회전하고_새_access를_반환한다() {
		Authentication authentication = Authentication.issue(1, PRESENTED_HASH);
		givenValidRefreshClaims();
		given(authenticationRepository.findByUserIdForUpdate(1))
			.willReturn(Optional.of(authentication));
		given(refreshTokenHasher.hash(PRESENTED_TOKEN)).willReturn(PRESENTED_HASH);
		given(userAccountPort.findById(1)).willReturn(Optional.of(userAccount("ACTIVE")));
		given(jwtTokenProvider.issue(
			eq(1),
			eq(UserAccountRole.USER),
			any(Instant.class)
		)).willReturn(NEXT_TOKENS);
		given(refreshTokenHasher.hash(NEXT_TOKENS.refreshToken())).willReturn(NEXT_HASH);

		RefreshResult result = authService.refresh(PRESENTED_TOKEN);

		assertThat(result).isEqualTo(new RefreshResult(
			new RefreshResponse(NEXT_TOKENS.accessToken()),
			NEXT_TOKENS.refreshToken()
		));
		assertThat(authentication.getRefreshTokenHash()).isEqualTo(NEXT_HASH);
		then(authenticationRepository).should().findByUserIdForUpdate(1);
	}

	@Test
	void 이미_회전된_refresh_token은_거절한다() {
		Authentication authentication = Authentication.issue(1, PRESENTED_HASH);
		givenValidRefreshClaims();
		given(authenticationRepository.findByUserIdForUpdate(1))
			.willReturn(Optional.of(authentication));
		given(refreshTokenHasher.hash(PRESENTED_TOKEN)).willReturn("c".repeat(64));

		assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
			.isInstanceOf(InvalidRefreshTokenException.class);

		assertThat(authentication.getRefreshTokenHash()).isEqualTo(PRESENTED_HASH);
		then(userAccountPort).shouldHaveNoInteractions();
		then(jwtTokenProvider).should().parseRefresh(PRESENTED_TOKEN);
		then(jwtTokenProvider).shouldHaveNoMoreInteractions();
	}

	@Test
	void 저장된_인증_정보가_없으면_refresh를_거절한다() {
		givenValidRefreshClaims();
		given(authenticationRepository.findByUserIdForUpdate(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
			.isInstanceOf(InvalidRefreshTokenException.class);

		then(refreshTokenHasher).shouldHaveNoInteractions();
		then(userAccountPort).shouldHaveNoInteractions();
	}

	@Test
	void 사용자가_없으면_refresh를_거절한다() {
		Authentication authentication = Authentication.issue(1, PRESENTED_HASH);
		givenStoredAuthentication(authentication);
		given(userAccountPort.findById(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
			.isInstanceOf(InvalidRefreshTokenException.class);

		assertThat(authentication.getRefreshTokenHash()).isEqualTo(PRESENTED_HASH);
		then(jwtTokenProvider).should().parseRefresh(PRESENTED_TOKEN);
		then(jwtTokenProvider).shouldHaveNoMoreInteractions();
	}

	@Test
	void 비활성_사용자는_refresh를_거절한다() {
		Authentication authentication = Authentication.issue(1, PRESENTED_HASH);
		givenStoredAuthentication(authentication);
		given(userAccountPort.findById(1)).willReturn(Optional.of(userAccount("SUSPENDED")));

		assertThatThrownBy(() -> authService.refresh(PRESENTED_TOKEN))
			.isInstanceOf(InvalidRefreshTokenException.class);

		assertThat(authentication.getRefreshTokenHash()).isEqualTo(PRESENTED_HASH);
		then(jwtTokenProvider).should().parseRefresh(PRESENTED_TOKEN);
		then(jwtTokenProvider).shouldHaveNoMoreInteractions();
	}

	private void givenStoredAuthentication(Authentication authentication) {
		givenValidRefreshClaims();
		given(authenticationRepository.findByUserIdForUpdate(1))
			.willReturn(Optional.of(authentication));
		given(refreshTokenHasher.hash(PRESENTED_TOKEN)).willReturn(PRESENTED_HASH);
	}

	private void givenValidRefreshClaims() {
		given(jwtTokenProvider.parseRefresh(PRESENTED_TOKEN))
			.willReturn(new TokenClaims(1, TokenType.REFRESH));
	}

	private UserAccount userAccount(String status) {
		return new UserAccount(
			1,
			"collector@example.com",
			"collector",
			UserAccountRole.USER,
			status,
			"encrypted-password",
			"salt"
		);
	}
}
