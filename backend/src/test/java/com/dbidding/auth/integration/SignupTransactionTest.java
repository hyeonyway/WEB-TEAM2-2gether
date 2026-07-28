package com.dbidding.auth.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.dbidding.auth.dto.SignupRequest;
import com.dbidding.auth.dto.SignupResponse;
import com.dbidding.auth.port.WalletProvisioningPort;
import com.dbidding.auth.repository.AuthenticationRepository;
import com.dbidding.auth.service.AuthService;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.repository.WalletRepository;

@SpringBootTest
class SignupTransactionTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@MockitoSpyBean
	private WalletProvisioningPort walletProvisioningPort;

	@AfterEach
	void cleanUp() {
		authenticationRepository.deleteAll();
		walletRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 회원가입하면_사용자와_잔액_0원_지갑만_함께_생성된다() {
		SignupRequest request = new SignupRequest(
			"signup-success@example.com",
			"Password123!",
			"signup-success"
		);

		SignupResponse response = authService.signup(request);

		User user = userRepository.findById(response.id()).orElseThrow();
		Wallet wallet = walletRepository.findByUserId(response.id()).orElseThrow();
		assertThat(user.getEmail()).isEqualTo(request.email());
		assertThat(user.getEncryptedPassword())
			.isNotEqualTo(request.password())
			.hasSize(64);
		assertThat(user.getSalt()).hasSize(32);
		assertThat(wallet.getPoint()).isZero();
		assertThat(authenticationRepository.findByUserId(response.id())).isEmpty();
	}

	@Test
	void 지갑_생성에_실패하면_사용자_저장도_롤백된다() {
		SignupRequest request = new SignupRequest(
			"signup-rollback@example.com",
			"Password123!",
			"signup-rollback"
		);
		doThrow(new IllegalStateException("wallet creation failed"))
			.when(walletProvisioningPort)
			.createFor(any(Integer.class));

		assertThatThrownBy(() -> authService.signup(request))
			.isInstanceOf(IllegalStateException.class);

		assertThat(userRepository.existsByEmail(request.email())).isFalse();
		assertThat(walletRepository.findAll()).isEmpty();
	}
}
