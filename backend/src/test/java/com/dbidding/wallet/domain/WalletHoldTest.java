package com.dbidding.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.dbidding.wallet.exception.InvalidWalletHoldStateException;

class WalletHoldTest {

	@Test
	void HELD는_금액을_늘릴_수_있다() {
		WalletHold hold = WalletHold.held(1, 20, 11_000L);

		hold.increaseTo(16_000L);

		assertThat(hold.getAmount()).isEqualTo(16_000L);
		assertThat(hold.getStatus()).isEqualTo(HoldStatus.HELD);
	}

	@Test
	void HELD_금액은_줄일_수_없다() {
		WalletHold hold = WalletHold.held(1, 20, 11_000L);

		assertThatThrownBy(() -> hold.increaseTo(10_000L))
			.isInstanceOf(InvalidWalletHoldStateException.class);
		assertThat(hold.getAmount()).isEqualTo(11_000L);
	}

	@Test
	void HELD는_RELEASED로_한_번만_전이한다() {
		Instant releasedAt = Instant.parse("2026-07-29T00:00:00Z");
		WalletHold hold = WalletHold.held(1, 20, 11_000L);

		hold.release(releasedAt);

		assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
		assertThat(hold.getReleasedAt()).isEqualTo(releasedAt);
		assertThatThrownBy(() -> hold.capture(Instant.now()))
			.isInstanceOf(InvalidWalletHoldStateException.class);
	}

	@Test
	void HELD는_CAPTURED로_전이한다() {
		Instant capturedAt = Instant.parse("2026-07-29T00:00:00Z");
		WalletHold hold = WalletHold.held(1, 20, 11_000L);

		hold.capture(capturedAt);

		assertThat(hold.getStatus()).isEqualTo(HoldStatus.CAPTURED);
		assertThat(hold.getReleasedAt()).isEqualTo(capturedAt);
		assertThatThrownBy(() -> hold.release(Instant.now()))
			.isInstanceOf(InvalidWalletHoldStateException.class);
	}
}
