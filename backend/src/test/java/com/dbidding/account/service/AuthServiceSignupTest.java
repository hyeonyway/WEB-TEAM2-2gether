package com.dbidding.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import java.sql.SQLException;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.dbidding.account.domain.Account;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.exception.DuplicateEmailException;
import com.dbidding.account.exception.DuplicateNicknameException;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.port.WalletProvisioningPort;
import com.dbidding.account.repository.AuthenticationRepository;
import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.account.token.RefreshTokenHasher;

@ExtendWith(MockitoExtension.class)
class AuthServiceSignupTest {

	private static final SignupRequest REQUEST = new SignupRequest(
		"collector@example.com",
		"Password123!",
		"collector"
	);

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
	void 중복_이메일이면_사용자와_지갑을_생성하지_않는다() {
		given(accountRepository.existsByEmail(REQUEST.email())).willReturn(true);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isInstanceOf(DuplicateEmailException.class);

		then(accountRepository).should(never()).saveAndFlush(any(Account.class));
		then(passwordHasher).shouldHaveNoInteractions();
		then(walletProvisioningPort).shouldHaveNoInteractions();
	}

	@Test
	void 중복_닉네임이면_사용자와_지갑을_생성하지_않는다() {
		given(accountRepository.existsByNickname(REQUEST.nickname())).willReturn(true);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isInstanceOf(DuplicateNicknameException.class);

		then(accountRepository).should(never()).saveAndFlush(any(Account.class));
		then(passwordHasher).shouldHaveNoInteractions();
		then(walletProvisioningPort).shouldHaveNoInteractions();
	}

	@Test
	void 회원가입하면_해시된_비밀번호로_사용자와_지갑을_생성한다() {
		PasswordHash passwordHash = new PasswordHash("encrypted-password", "salt");
		Account savedAccount = spy(Account.create(
			REQUEST.email(),
			REQUEST.nickname(),
			passwordHash.encryptedPassword(),
			passwordHash.salt()
		));
		given(savedAccount.getId()).willReturn(1);
		given(passwordHasher.hash(REQUEST.password())).willReturn(passwordHash);
		given(accountRepository.saveAndFlush(any(Account.class))).willReturn(savedAccount);

		SignupResponse response = authService.signup(REQUEST);

		assertThat(response).isEqualTo(new SignupResponse(
			1,
			REQUEST.email(),
			REQUEST.nickname(),
			"USER",
			"ACTIVE"
		));
		then(walletProvisioningPort).should().createFor(1);
	}

	@Test
	void 이메일_UNIQUE_제약이_충돌하면_기존_중복_이메일_예외로_변환한다() {
		PasswordHash passwordHash = new PasswordHash("encrypted-password", "salt");
		DataIntegrityViolationException duplicateEmail =
			dataIntegrityViolation("users.uk_users_email");
		given(passwordHasher.hash(REQUEST.password())).willReturn(passwordHash);
		given(accountRepository.saveAndFlush(any(Account.class))).willThrow(duplicateEmail);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isInstanceOf(DuplicateEmailException.class)
			.hasCause(duplicateEmail);
	}

	@Test
	void 닉네임_UNIQUE_제약이_충돌하면_기존_중복_닉네임_예외로_변환한다() {
		PasswordHash passwordHash = new PasswordHash("encrypted-password", "salt");
		DataIntegrityViolationException duplicateNickname =
			dataIntegrityViolation("`uk_users_nickname`");
		given(passwordHasher.hash(REQUEST.password())).willReturn(passwordHash);
		given(accountRepository.saveAndFlush(any(Account.class))).willThrow(duplicateNickname);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isInstanceOf(DuplicateNicknameException.class)
			.hasCause(duplicateNickname);
	}

	@Test
	void UNIQUE가_아닌_무결성_예외는_그대로_전파한다() {
		PasswordHash passwordHash = new PasswordHash("encrypted-password", "salt");
		DataIntegrityViolationException unrelatedConstraint =
			dataIntegrityViolation("fk_users_unrelated");
		given(passwordHasher.hash(REQUEST.password())).willReturn(passwordHash);
		given(accountRepository.saveAndFlush(any(Account.class))).willThrow(unrelatedConstraint);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isSameAs(unrelatedConstraint);
	}

	private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
			"constraint violation",
			new SQLException("constraint violation"),
			constraintName
		);
		return new DataIntegrityViolationException("data integrity violation", constraintViolation);
	}
}
