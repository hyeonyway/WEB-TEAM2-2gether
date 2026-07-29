package com.dbidding.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.dbidding.auth.domain.Authentication;
import com.dbidding.auth.exception.InvalidRefreshTokenException;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.auth.repository.AuthenticationRepository;
import com.dbidding.auth.token.IssuedTokens;
import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.auth.token.RefreshTokenHasher;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;

@SpringBootTest
class AuthServiceRefreshConcurrencyTest {

	@Autowired
	private AuthService authService;

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JwtTokenProvider jwtTokenProvider;

	@Autowired
	private RefreshTokenHasher refreshTokenHasher;

	private Integer userId;
	private String presentedToken;

	@BeforeEach
	void setUp() {
		User user = userRepository.saveAndFlush(User.create(
			"concurrent-refresh@example.com",
			"concurrent-refresh",
			"a".repeat(64),
			"b".repeat(32)
		));
		userId = user.getId();

		IssuedTokens tokens = jwtTokenProvider.issue(userId, UserAccountRole.USER, Instant.now());
		presentedToken = tokens.refreshToken();
		authenticationRepository.saveAndFlush(Authentication.issue(
			userId,
			refreshTokenHasher.hash(presentedToken)
		));
	}

	@AfterEach
	void cleanUp() {
		authenticationRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 동일한_refresh_token의_동시_요청은_하나만_성공한다() throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<RefreshAttempt> first = executor.submit(() -> refreshAfterSignal(ready, start));
			Future<RefreshAttempt> second = executor.submit(() -> refreshAfterSignal(ready, start));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

			start.countDown();
			List<RefreshAttempt> attempts = List.of(
				first.get(10, TimeUnit.SECONDS),
				second.get(10, TimeUnit.SECONDS)
			);

			assertThat(attempts).filteredOn(RefreshAttempt::succeeded).hasSize(1);
			assertThat(attempts).filteredOn(attempt -> !attempt.succeeded()).hasSize(1);

			RefreshResult winner = attempts.stream()
				.filter(RefreshAttempt::succeeded)
				.map(RefreshAttempt::result)
				.findFirst()
				.orElseThrow();
			assertThat(authenticationRepository.findByUserId(userId))
				.isPresent()
				.get()
				.extracting(Authentication::getRefreshTokenHash)
				.isEqualTo(refreshTokenHasher.hash(winner.refreshToken()));
		} finally {
			executor.shutdownNow();
		}
	}

	private RefreshAttempt refreshAfterSignal(
		CountDownLatch ready,
		CountDownLatch start
	) {
		ready.countDown();
		await(start);
		try {
			return RefreshAttempt.success(authService.refresh(presentedToken));
		} catch (InvalidRefreshTokenException exception) {
			return RefreshAttempt.rejected();
		}
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private record RefreshAttempt(
		boolean succeeded,
		RefreshResult result
	) {

		private static RefreshAttempt success(RefreshResult result) {
			return new RefreshAttempt(true, result);
		}

		private static RefreshAttempt rejected() {
			return new RefreshAttempt(false, null);
		}
	}
}
