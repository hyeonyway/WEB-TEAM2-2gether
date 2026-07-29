package com.dbidding.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.dbidding.auth.domain.Authentication;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;

@SpringBootTest
class AuthenticationLockConcurrencyTest {

	private static final String ORIGINAL_HASH = "a".repeat(64);
	private static final String ROTATED_HASH = "b".repeat(64);

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TransactionTemplate transactionTemplate;

	private Integer userId;

	@BeforeEach
	void setUp() {
		User user = userRepository.saveAndFlush(User.create(
			"authentication-lock@example.com",
			"authentication-lock",
			"c".repeat(64),
			"d".repeat(32)
		));
		userId = user.getId();
		authenticationRepository.saveAndFlush(Authentication.issue(userId, ORIGINAL_HASH));
	}

	@AfterEach
	void cleanUp() {
		authenticationRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 쓰기_잠금이_해제될_때까지_두_번째_조회가_대기한다() throws Exception {
		CountDownLatch firstLocked = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		CountDownLatch secondStarted = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> first = executor.submit(() -> rotateWhileHoldingLock(firstLocked, releaseFirst));
			assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();

			Future<String> second = executor.submit(() -> findHashAfterSignal(secondStarted));
			assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
			assertThatThrownBy(() -> second.get(300, TimeUnit.MILLISECONDS))
				.isInstanceOf(TimeoutException.class);

			releaseFirst.countDown();
			first.get(5, TimeUnit.SECONDS);
			assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(ROTATED_HASH);
		} finally {
			releaseFirst.countDown();
			executor.shutdownNow();
		}
	}

	private void rotateWhileHoldingLock(
		CountDownLatch firstLocked,
		CountDownLatch releaseFirst
	) {
		transactionTemplate.executeWithoutResult(status -> {
			Authentication authentication = authenticationRepository.findByUserIdForUpdate(userId)
				.orElseThrow();
			firstLocked.countDown();
			await(releaseFirst);
			authentication.rotate(ROTATED_HASH);
		});
	}

	private String findHashAfterSignal(CountDownLatch secondStarted) {
		return transactionTemplate.execute(status -> {
			secondStarted.countDown();
			return authenticationRepository.findByUserIdForUpdate(userId)
				.orElseThrow()
				.getRefreshTokenHash();
		});
	}

	private void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}
}
