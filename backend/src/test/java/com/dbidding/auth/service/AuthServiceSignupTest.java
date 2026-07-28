package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.auth.dto.SignupRequest;
import com.dbidding.auth.dto.SignupResponse;
import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.password.PasswordHash;
import com.dbidding.auth.password.PasswordHasher;
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountPort;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.auth.port.WalletProvisioningPort;

@ExtendWith(MockitoExtension.class)
class AuthServiceSignupTest {

	private static final SignupRequest REQUEST = new SignupRequest(
		"collector@example.com",
		"Password123!",
		"collector"
	);

	@Mock
	private UserAccountPort userAccountPort;

	@Mock
	private WalletProvisioningPort walletProvisioningPort;

	@Mock
	private PasswordHasher passwordHasher;

	private AuthService authService;

	@BeforeEach
	void setUp() {
		authService = new AuthService(userAccountPort, walletProvisioningPort, passwordHasher);
	}

	@Test
	void 중복_이메일이면_사용자와_지갑을_생성하지_않는다() {
		given(userAccountPort.existsByEmail(REQUEST.email())).willReturn(true);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isInstanceOf(DuplicateEmailException.class);

		then(userAccountPort).should(never()).create(any(), any(), any(), any());
		then(passwordHasher).shouldHaveNoInteractions();
		then(walletProvisioningPort).shouldHaveNoInteractions();
	}

	@Test
	void 중복_닉네임이면_사용자와_지갑을_생성하지_않는다() {
		given(userAccountPort.existsByNickname(REQUEST.nickname())).willReturn(true);

		assertThatThrownBy(() -> authService.signup(REQUEST))
			.isInstanceOf(DuplicateNicknameException.class);

		then(userAccountPort).should(never()).create(any(), any(), any(), any());
		then(passwordHasher).shouldHaveNoInteractions();
		then(walletProvisioningPort).shouldHaveNoInteractions();
	}

	@Test
	void 회원가입하면_해시된_비밀번호로_사용자와_지갑을_생성한다() {
		PasswordHash passwordHash = new PasswordHash("encrypted-password", "salt");
		UserAccount savedUser = new UserAccount(
			1,
			REQUEST.email(),
			REQUEST.nickname(),
			UserAccountRole.USER,
			"ACTIVE",
			passwordHash.encryptedPassword(),
			passwordHash.salt()
		);
		given(passwordHasher.hash(REQUEST.password())).willReturn(passwordHash);
		given(userAccountPort.create(
			REQUEST.email(),
			REQUEST.nickname(),
			passwordHash.encryptedPassword(),
			passwordHash.salt()
		)).willReturn(savedUser);

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
}
