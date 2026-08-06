package com.dbidding.account.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

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
import com.dbidding.account.exception.InvalidCredentialsException;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
class CredentialAuthenticationServiceTest {

	private static final String EMAIL = "collector@example.com";
	private static final String PASSWORD = "Password123!";

	@Mock
	private AccountRepository accountRepository;

	@Mock
	private PasswordHasher passwordHasher;

	private CredentialAuthenticationService service;

	@BeforeEach
	void setUp() {
		service = new CredentialAuthenticationService(accountRepository, passwordHasher);
	}

	@Test
	void 유효한_자격_증명이면_인증된_계정_정보를_반환한다() {
		Account account = account(AccountStatus.ACTIVE);
		given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
		given(passwordHasher.matches(PASSWORD, account.getSalt(), account.getEncryptedPassword()))
			.willReturn(true);

		AuthenticatedAccount authenticated = service.authenticate(EMAIL, PASSWORD);

		assertThat(authenticated).isEqualTo(new AuthenticatedAccount(1, AccountRole.USER));
	}

	@Test
	void 존재하지_않는_이메일도_더미_해시를_검증하고_동일한_예외를_던진다() {
		given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
			.isInstanceOf(InvalidCredentialsException.class);
		then(passwordHasher).should().matches(eq(PASSWORD), anyString(), anyString());
	}

	@Test
	void 잘못된_비밀번호면_인증에_실패한다() {
		Account account = account(AccountStatus.ACTIVE);
		given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
		given(passwordHasher.matches(PASSWORD, account.getSalt(), account.getEncryptedPassword()))
			.willReturn(false);

		assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	@ParameterizedTest
	@EnumSource(value = AccountStatus.class, names = {"SUSPENDED", "WITHDRAWN"})
	void 비활성_계정이면_인증에_실패한다(AccountStatus status) {
		Account account = account(status);
		given(accountRepository.findByEmail(EMAIL)).willReturn(Optional.of(account));
		given(passwordHasher.matches(PASSWORD, account.getSalt(), account.getEncryptedPassword()))
			.willReturn(true);

		assertThatThrownBy(() -> service.authenticate(EMAIL, PASSWORD))
			.isInstanceOf(InvalidCredentialsException.class);
	}

	private Account account(AccountStatus status) {
		Account account = Account.create(EMAIL, "collector", "encrypted-password", "salt");
		ReflectionTestUtils.setField(account, "id", 1);
		ReflectionTestUtils.setField(account, "status", status);
		return account;
	}
}
