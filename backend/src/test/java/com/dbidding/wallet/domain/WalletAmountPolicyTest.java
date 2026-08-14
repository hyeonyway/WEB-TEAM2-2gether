package com.dbidding.wallet.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.dbidding.wallet.exception.InvalidWalletAmountException;

class WalletAmountPolicyTest {

	@Test
	void 거래_금액은_1000억원까지_허용한다() {
		assertThatCode(() -> WalletAmountPolicy.validateTransactionAmount(100_000_000_000L))
			.doesNotThrowAnyException();

		assertThatThrownBy(() -> WalletAmountPolicy.validateTransactionAmount(100_000_000_001L))
			.isInstanceOf(InvalidWalletAmountException.class);
	}

	@Test
	void 지갑과_경매_금액은_1조원까지_허용한다() {
		assertThatCode(() -> WalletAmountPolicy.validateBalanceAmount(1_000_000_000_000L))
			.doesNotThrowAnyException();

		assertThatThrownBy(() -> WalletAmountPolicy.validateBalanceAmount(1_000_000_000_001L))
			.isInstanceOf(InvalidWalletAmountException.class);
	}
}
