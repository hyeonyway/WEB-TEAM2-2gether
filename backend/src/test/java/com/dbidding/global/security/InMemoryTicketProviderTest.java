package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.global.exception.UnauthorizedException;

@ExtendWith(MockitoExtension.class)
class InMemoryTicketProviderTest {

	private static final Instant NOW = Instant.parse("2026-07-30T00:00:00Z");

	@Mock
	private Clock clock;

	private ExecutorService executor;

	@AfterEach
	void tearDown() {
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	@Test
	void 발급한_티켓으로_30초_안에_사용자_ID를_한_번만_조회한다() {
		given(clock.instant()).willReturn(NOW, NOW.plusSeconds(29));
		InMemoryTicketProvider provider = new InMemoryTicketProvider(clock);

		String ticket = provider.issue(7);

		assertThat(provider.validateAndConsume(ticket)).isEqualTo(7);
		assertThatThrownBy(() -> provider.validateAndConsume(ticket))
			.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void 사용자마다_예측하기_어려운_서로_다른_티켓을_발급한다() {
		given(clock.instant()).willReturn(NOW);
		InMemoryTicketProvider provider = new InMemoryTicketProvider(clock);

		String first = provider.issue(7);
		String second = provider.issue(8);

		assertThat(first).isNotBlank().isNotEqualTo(second);
	}

	@Test
	void 발급_후_30초가_되면_티켓을_거절한다() {
		given(clock.instant()).willReturn(NOW, NOW.plusSeconds(30));
		InMemoryTicketProvider provider = new InMemoryTicketProvider(clock);
		String ticket = provider.issue(7);

		assertThatThrownBy(() -> provider.validateAndConsume(ticket))
			.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void 존재하지_않거나_빈_티켓을_거절한다() {
		InMemoryTicketProvider provider = new InMemoryTicketProvider(clock);

		assertThatThrownBy(() -> provider.validateAndConsume("unknown"))
			.isInstanceOf(UnauthorizedException.class);
		assertThatThrownBy(() -> provider.validateAndConsume(""))
			.isInstanceOf(UnauthorizedException.class);
		assertThatThrownBy(() -> provider.validateAndConsume(null))
			.isInstanceOf(UnauthorizedException.class);
	}

	@Test
	void 만료된_미사용_티켓을_정리한다() {
		given(clock.instant()).willReturn(NOW, NOW.plusSeconds(31));
		InMemoryTicketProvider provider = new InMemoryTicketProvider(clock);
		provider.issue(7);

		provider.removeExpiredTickets();

		assertThat(provider.ticketCount()).isZero();
	}

	@Test
	void 같은_티켓을_동시에_검증하면_한_요청만_성공한다() throws Exception {
		Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
		InMemoryTicketProvider provider = new InMemoryTicketProvider(fixedClock);
		String ticket = provider.issue(7);
		executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);

		List<Future<Boolean>> results = List.of(
			executor.submit(() -> validate(provider, ticket, ready, start)),
			executor.submit(() -> validate(provider, ticket, ready, start))
		);
		ready.await();
		start.countDown();

		long successCount = 0;
		for (Future<Boolean> result : results) {
			if (result.get()) {
				successCount++;
			}
		}
		assertThat(successCount).isEqualTo(1);
	}

	private boolean validate(
		InMemoryTicketProvider provider,
		String ticket,
		CountDownLatch ready,
		CountDownLatch start
	) {
		ready.countDown();
		await(start);
		try {
			return provider.validateAndConsume(ticket) == 7;
		} catch (UnauthorizedException exception) {
			return false;
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
}
