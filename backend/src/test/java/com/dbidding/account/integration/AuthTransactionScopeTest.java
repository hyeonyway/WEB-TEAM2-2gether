package com.dbidding.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.authentication.jwt.AuthenticationRepository;
import com.dbidding.account.authentication.jwt.JwtAuthenticationStrategy;
import com.dbidding.account.domain.Account;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.port.WalletProvisioningPort;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.service.SignupService;
import com.dbidding.account.support.AccountMySqlIntegrationTest;

class AuthTransactionScopeTest extends AccountMySqlIntegrationTest {

	@Autowired
	private CredentialAuthenticationService credentialAuthenticationService;

	@Autowired
	private JwtAuthenticationStrategy jwtAuthenticationStrategy;

	@Autowired
	private SignupService signupService;

	@Autowired
	private AccountRepository accountRepository;

	@MockitoSpyBean
	private PasswordHasher passwordHasher;

	@MockitoSpyBean
	private AuthenticationRepository authenticationRepository;

	@MockitoSpyBean
	private WalletProvisioningPort walletProvisioningPort;

	@Test
	void 로그인_비밀번호_검증은_트랜잭션_밖에서_하고_refresh_hash_저장은_트랜잭션_안에서_한다() {
		Account account = accountRepository.saveAndFlush(Account.create(
			"login-scope@example.com",
			"login-scope",
			"a".repeat(64),
			"b".repeat(32)
		));
		AtomicBoolean transactionActiveDuringPasswordMatch = new AtomicBoolean(true);
		AtomicBoolean transactionActiveDuringRefreshTokenUpsert = new AtomicBoolean(false);
		doAnswer(invocation -> {
			transactionActiveDuringPasswordMatch.set(
				TransactionSynchronizationManager.isActualTransactionActive()
			);
			return true;
		}).when(passwordHasher).matches(anyString(), anyString(), anyString());
		doAnswer(invocation -> {
			transactionActiveDuringRefreshTokenUpsert.set(
				TransactionSynchronizationManager.isActualTransactionActive()
			);
			return null;
		}).when(authenticationRepository).upsertRefreshTokenHash(eq(account.getId()), anyString());

		AuthenticatedAccount authenticatedAccount = credentialAuthenticationService.authenticate(
			"login-scope@example.com",
			"Password123!"
		);
		jwtAuthenticationStrategy.establish(authenticatedAccount, new MockHttpServletRequest());

		assertThat(transactionActiveDuringPasswordMatch).isFalse();
		assertThat(transactionActiveDuringRefreshTokenUpsert).isTrue();
	}

	@Test
	void 회원가입_비밀번호_해싱은_트랜잭션_밖에서_하고_계정과_지갑_생성은_트랜잭션_안에서_한다() {
		AtomicBoolean transactionActiveDuringPasswordHash = new AtomicBoolean(true);
		AtomicBoolean transactionActiveDuringWalletProvisioning = new AtomicBoolean(false);
		doAnswer(invocation -> {
			transactionActiveDuringPasswordHash.set(
				TransactionSynchronizationManager.isActualTransactionActive()
			);
			return new PasswordHash("a".repeat(64), "b".repeat(32));
		}).when(passwordHasher).hash(anyString());
		doAnswer(invocation -> {
			transactionActiveDuringWalletProvisioning.set(
				TransactionSynchronizationManager.isActualTransactionActive()
			);
			return invocation.callRealMethod();
		}).when(walletProvisioningPort).createFor(org.mockito.ArgumentMatchers.anyInt());

		var response = signupService.signup(new SignupRequest(
			"signup-scope@example.com",
			"Password123!",
			"signup-scope"
		));

		assertThat(transactionActiveDuringPasswordHash).isFalse();
		assertThat(transactionActiveDuringWalletProvisioning).isTrue();
		assertThat(accountRepository.findById(response.id())).isPresent();
	}
}
