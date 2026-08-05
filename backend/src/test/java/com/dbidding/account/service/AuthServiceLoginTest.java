package com.dbidding.account.service;

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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.dto.LoginRequest;
import com.dbidding.account.dto.LoginResponse;
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.port.WalletProvisioningPort;
import com.dbidding.account.repository.AuthenticationRepository;
import com.dbidding.account.token.IssuedTokens;
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.RefreshTokenHasher;

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
		AuthTransactionService authTransactionService = new AuthTransactionService(
			accountRepository,
			walletProvisioningPort,
			authenticationRepository
		);
		authService = new AuthService(
			accountRepository,
			passwordHasher,
			authenticationRepository,
			jwtTokenProvider,
			refreshTokenHasher,
			authTransactionService
		);
	}

	@Test
	void 존재하지_않는_이메일이면_동일한_인증_실패로_처리한다() {
		given(accountRepository.findByEmail(REQUEST.email())).willReturn(Optional.empty());

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
		Account account = account(AccountStatus.ACTIVE);
		given(accountRepository.findByEmail(REQUEST.email())).willReturn(Optional.of(account));
		given(passwordHasher.matches(
			REQUEST.password(),
			account.getSalt(),
			account.getEncryptedPassword()
		)).willReturn(false);

		assertThatThrownBy(() -> authService.login(REQUEST))
			.isInstanceOf(InvalidCredentialsException.class);

		then(jwtTokenProvider).shouldHaveNoInteractions();
		then(authenticationRepository).shouldHaveNoInteractions();
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
	void 비활성_계정이면_토큰을_발급하지_않는다(AccountStatus status) {
		Account account = account(status);
		given(accountRepository.findByEmail(REQUEST.email())).willReturn(Optional.of(account));
		given(passwordHasher.matches(
			REQUEST.password(),
			account.getSalt(),
			account.getEncryptedPassword()
		)).willReturn(true);

		assertThatThrownBy(() -> authService.login(REQUEST))
			.isInstanceOf(InvalidCredentialsException.class);

		then(jwtTokenProvider).shouldHaveNoInteractions();
		then(authenticationRepository).shouldHaveNoInteractions();
	}

	@Test
	void 로그인하면_refresh_hash를_저장하고_access를_반환한다() {
		Account account = account(AccountStatus.ACTIVE);
		givenSuccessfulCredentialValidation(account);
		given(jwtTokenProvider.issue(
			eq(account.getId()),
			eq(AccountRole.USER),
			any(Instant.class)
		)).willReturn(TOKENS);
		given(refreshTokenHasher.hash(TOKENS.refreshToken())).willReturn(REFRESH_TOKEN_HASH);

		LoginResult result = authService.login(REQUEST);

		assertThat(result).isEqualTo(new LoginResult(
			new LoginResponse(TOKENS.accessToken()),
			TOKENS.refreshToken()
		));
		then(authenticationRepository).should().upsertRefreshTokenHash(
			account.getId(),
			REFRESH_TOKEN_HASH
		);
	}

	private void givenSuccessfulCredentialValidation(Account account) {
		given(accountRepository.findByEmail(REQUEST.email())).willReturn(Optional.of(account));
		given(passwordHasher.matches(
			REQUEST.password(),
			account.getSalt(),
			account.getEncryptedPassword()
		)).willReturn(true);
	}

	private Account account(AccountStatus status) {
		Account account = Account.create(
			REQUEST.email(),
			"collector",
			"encrypted-password",
			"salt"
		);
		ReflectionTestUtils.setField(account, "id", 1);
		ReflectionTestUtils.setField(account, "status", status);
		return account;
	}
}
