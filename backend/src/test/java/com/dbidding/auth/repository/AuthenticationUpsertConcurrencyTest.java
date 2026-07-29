package com.dbidding.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
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
import org.springframework.transaction.support.TransactionTemplate;

import com.dbidding.auth.domain.Authentication;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;

@SpringBootTest
class AuthenticationUpsertConcurrencyTest {

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
			"concurrent-login@example.com",
			"concurrent-login",
			"a".repeat(64),
			"b".repeat(32)
		));
		userId = user.getId();
	}

	@AfterEach
	void cleanUp() {
		authenticationRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void 두_최초_로그인이_동시에_refresh_hash를_저장해도_한_row로_완료한다() throws Exception {
		String firstHash = "c".repeat(64);
		String secondHash = "d".repeat(64);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		try {
			Future<?> first = executor.submit(() -> upsertAfterSignal(ready, start, firstHash));
			Future<?> second = executor.submit(() -> upsertAfterSignal(ready, start, secondHash));
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();

			start.countDown();
			first.get(10, TimeUnit.SECONDS);
			second.get(10, TimeUnit.SECONDS);

			assertThat(authenticationRepository.findAll())
				.singleElement()
				.extracting(Authentication::getRefreshTokenHash)
				.isIn(Set.of(firstHash, secondHash));
		} finally {
			executor.shutdownNow();
		}
	}

	private void upsertAfterSignal(
		CountDownLatch ready,
		CountDownLatch start,
		String refreshTokenHash
	) {
		transactionTemplate.executeWithoutResult(status -> {
			ready.countDown();
			await(start);
			authenticationRepository.upsertRefreshTokenHash(userId, refreshTokenHash);
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
