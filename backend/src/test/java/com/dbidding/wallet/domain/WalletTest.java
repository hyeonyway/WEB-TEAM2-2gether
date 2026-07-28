package com.dbidding.wallet.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WalletTest {

	@Test
	void 신규_지갑은_잔액_0으로_생성된다() {
		Wallet wallet = Wallet.open(1);

		assertThat(wallet.getUserId()).isEqualTo(1);
		assertThat(wallet.getPoint()).isZero();
	}
}
