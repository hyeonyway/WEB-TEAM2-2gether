package com.dbidding.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.dto.SignupResponse;
import com.dbidding.account.port.WalletProvisioningPort;
import com.dbidding.account.authentication.jwt.AuthenticationRepository;
import com.dbidding.account.service.SignupService;
import com.dbidding.account.domain.Account;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.support.AccountMySqlIntegrationTest;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.repository.WalletRepository;

class SignupTransactionTest extends AccountMySqlIntegrationTest {

	@Autowired
	private SignupService signupService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private WalletRepository walletRepository;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@MockitoSpyBean
	private WalletProvisioningPort walletProvisioningPort;

	@Test
	void 회원가입하면_사용자와_잔액_0원_지갑만_함께_생성된다() {
		SignupRequest request = new SignupRequest(
			"signup-success@example.com",
			"Password123!",
			"signup-success"
		);

		SignupResponse response = signupService.signup(request);

		Account account = accountRepository.findById(response.id()).orElseThrow();
		Wallet wallet = walletRepository.findByUserId(response.id()).orElseThrow();
		assertThat(account.getEmail()).isEqualTo(request.email());
		assertThat(account.getEncryptedPassword())
			.isNotEqualTo(request.password())
			.hasSize(64);
		assertThat(account.getSalt()).hasSize(32);
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

		assertThatThrownBy(() -> signupService.signup(request))
			.isInstanceOf(IllegalStateException.class);

		assertThat(accountRepository.existsByEmail(request.email())).isFalse();
		assertThat(walletRepository.findAll()).isEmpty();
	}
}
