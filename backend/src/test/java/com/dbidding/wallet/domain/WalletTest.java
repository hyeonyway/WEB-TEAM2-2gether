package com.dbidding.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WalletTest {

	@Test
	void 신규_지갑은_잔액_0으로_생성된다() {
		Wallet wallet = Wallet.open(1);

		assertThat(wallet.getUserId()).isEqualTo(1);
		assertThat(wallet.getPoint()).isZero();
	}

	@Test
	void 지갑은_양수_금액만_증감할_수_있다() {
		Wallet wallet = Wallet.open(1);

		wallet.credit(10_000L);
		wallet.debit(3_000L);

		assertThat(wallet.getPoint()).isEqualTo(7_000L);
		assertThatThrownBy(() -> wallet.credit(0L))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> wallet.debit(-1L))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 잔액보다_큰_금액은_차감할_수_없다() {
		Wallet wallet = Wallet.open(1);
		wallet.credit(10_000L);

		assertThatThrownBy(() -> wallet.debit(10_001L))
			.isInstanceOf(IllegalStateException.class);
		assertThat(wallet.getPoint()).isEqualTo(10_000L);
	}
}
