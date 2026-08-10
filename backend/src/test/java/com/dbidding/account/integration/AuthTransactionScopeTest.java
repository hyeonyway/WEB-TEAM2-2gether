package com.dbidding.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.dbidding.account.authentication.AuthenticatedAccount;
import com.dbidding.account.authentication.CredentialAuthenticationService;
import com.dbidding.account.authentication.jwt.AuthenticationRepository;
import com.dbidding.account.authentication.jwt.JwtAuthenticationStrategy;
import com.dbidding.account.domain.Account;
import com.dbidding.account.dto.SignupRequest;
import com.dbidding.account.password.PasswordHash;
import com.dbidding.account.password.PasswordHasher;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.account.service.SignupService;
import com.dbidding.account.support.AccountMySqlIntegrationTest;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.service.WalletService;

@Import(AuthTransactionScopeTest.WalletTestConfiguration.class)
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

	@Autowired
	private TransactionObservingWalletService walletService;

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
		walletService.resetTransactionObservation();
		doAnswer(invocation -> {
			transactionActiveDuringPasswordHash.set(
				TransactionSynchronizationManager.isActualTransactionActive()
			);
			return new PasswordHash("a".repeat(64), "b".repeat(32));
		}).when(passwordHasher).hash(anyString());
		var response = signupService.signup(new SignupRequest(
			"signup-scope@example.com",
			"Password123!",
			"signup-scope"
		));

		assertThat(transactionActiveDuringPasswordHash).isFalse();
		assertThat(walletService.wasTransactionActiveDuringProvisioning()).isTrue();
		assertThat(accountRepository.findById(response.id())).isPresent();
	}

	@TestConfiguration
	static class WalletTestConfiguration {

		@Bean
		@Primary
		TransactionObservingWalletService transactionObservingWalletService(
			WalletRepository walletRepository,
			PointRecordRepository pointRecordRepository,
			WalletHoldRepository walletHoldRepository,
			WalletMetrics walletMetrics,
			Clock clock
		) {
			return new TransactionObservingWalletService(
				walletRepository,
				pointRecordRepository,
				walletHoldRepository,
				walletMetrics,
				clock
			);
		}
	}

	static class TransactionObservingWalletService extends WalletService {

		private final AtomicBoolean transactionActiveDuringProvisioning = new AtomicBoolean();

		TransactionObservingWalletService(
			WalletRepository walletRepository,
			PointRecordRepository pointRecordRepository,
			WalletHoldRepository walletHoldRepository,
			WalletMetrics walletMetrics,
			Clock clock
		) {
			super(walletRepository, pointRecordRepository, walletHoldRepository, walletMetrics, clock);
		}

		void resetTransactionObservation() {
			transactionActiveDuringProvisioning.set(false);
		}

		boolean wasTransactionActiveDuringProvisioning() {
			return transactionActiveDuringProvisioning.get();
		}

		@Override
		@Transactional(propagation = Propagation.MANDATORY)
		public void provision(Integer userId) {
			transactionActiveDuringProvisioning.set(
				TransactionSynchronizationManager.isActualTransactionActive()
			);
			super.provision(userId);
		}
	}
}
