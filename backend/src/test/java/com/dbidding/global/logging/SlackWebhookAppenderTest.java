package com.dbidding.global.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class SlackWebhookAppenderTest {

	private static final Instant WINDOW_START = Instant.parse("2026-08-09T00:00:00Z");

	@Test
	void 같은_logger와_예외는_60초_동안_세_번까지만_전송한다() {
		SlackAlertRateLimiter rateLimiter = new SlackAlertRateLimiter(Duration.ofSeconds(60), 3);

		assertThat(rateLimiter.acquire("wallet", "InvalidWalletBalanceException", WINDOW_START).send()).isTrue();
		assertThat(rateLimiter.acquire("wallet", "InvalidWalletBalanceException", WINDOW_START.plusSeconds(1)).send()).isTrue();
		assertThat(rateLimiter.acquire("wallet", "InvalidWalletBalanceException", WINDOW_START.plusSeconds(2)).send()).isTrue();
		assertThat(rateLimiter.acquire("wallet", "InvalidWalletBalanceException", WINDOW_START.plusSeconds(3)).send()).isFalse();
	}

	@Test
	void 다음_윈도우의_첫_전송에_직전_윈도우_억제_건수를_함께_반환한다() {
		SlackAlertRateLimiter rateLimiter = new SlackAlertRateLimiter(Duration.ofSeconds(60), 3);
		for (int index = 0; index < 4; index++) {
			rateLimiter.acquire("wallet", "InvalidWalletBalanceException", WINDOW_START.plusSeconds(index));
		}

		SlackAlertRateLimiter.Decision decision = rateLimiter.acquire(
			"wallet",
			"InvalidWalletBalanceException",
			WINDOW_START.plusSeconds(60)
		);

		assertThat(decision.send()).isTrue();
		assertThat(decision.suppressedCount()).isEqualTo(1);
	}

	@Test
	void logger_또는_예외가_다르면_서로_다른_레이트리밋을_사용한다() {
		SlackAlertRateLimiter rateLimiter = new SlackAlertRateLimiter(Duration.ofSeconds(60), 1);
		rateLimiter.acquire("wallet", "InvalidWalletBalanceException", WINDOW_START);

		assertThat(rateLimiter.acquire("wallet", "WalletNotFoundException", WINDOW_START).send()).isTrue();
		assertThat(rateLimiter.acquire("auction", "InvalidWalletBalanceException", WINDOW_START).send()).isTrue();
	}
}
