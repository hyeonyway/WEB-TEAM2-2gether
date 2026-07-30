package com.dbidding.account.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.port.WalletProvisioningPort;
import com.dbidding.account.repository.AuthenticationRepository;
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.RefreshTokenHasher;

@ExtendWith(MockitoExtension.class)
class AuthServiceLogoutTest {

	private static final String REFRESH_TOKEN = "expired-or-malformed-refresh-token";
	private static final String REFRESH_TOKEN_HASH = "a".repeat(64);

	@Mock
	private AccountRepository accountRepository;

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
			accountRepository,
			walletProvisioningPort,
			passwordHasher,
			authenticationRepository,
			jwtTokenProvider,
			refreshTokenHasher
		);
	}

	@Test
	void refresh_token의_hash와_일치하는_인증_정보를_삭제한다() {
		given(refreshTokenHasher.hash(REFRESH_TOKEN)).willReturn(REFRESH_TOKEN_HASH);
		given(authenticationRepository.deleteByRefreshTokenHash(REFRESH_TOKEN_HASH)).willReturn(1);

		assertThatCode(() -> authService.logout(REFRESH_TOKEN))
			.doesNotThrowAnyException();

		then(authenticationRepository).should().deleteByRefreshTokenHash(REFRESH_TOKEN_HASH);
		then(jwtTokenProvider).shouldHaveNoInteractions();
	}

	@Test
	void 저장된_hash가_없어도_로그아웃은_성공한다() {
		given(refreshTokenHasher.hash(REFRESH_TOKEN)).willReturn(REFRESH_TOKEN_HASH);
		given(authenticationRepository.deleteByRefreshTokenHash(REFRESH_TOKEN_HASH)).willReturn(0);

		assertThatCode(() -> authService.logout(REFRESH_TOKEN))
			.doesNotThrowAnyException();
	}

	@Test
	void refresh_token이_없으면_저장소를_호출하지_않고_성공한다() {
		assertThatCode(() -> authService.logout(null))
			.doesNotThrowAnyException();
		assertThatCode(() -> authService.logout("  "))
			.doesNotThrowAnyException();

		then(refreshTokenHasher).shouldHaveNoInteractions();
		then(authenticationRepository).shouldHaveNoInteractions();
	}
}
