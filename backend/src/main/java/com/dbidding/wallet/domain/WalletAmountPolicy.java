package com.dbidding.wallet.domain;

import com.dbidding.wallet.exception.InvalidWalletAmountException;

public final class WalletAmountPolicy {

	public static final long MAX_TRANSACTION_AMOUNT = 100_000_000_000L;
	public static final long MAX_BALANCE = 1_000_000_000_000L;

	private WalletAmountPolicy() {
	}

	public static void validateTransactionAmount(long amount) {
		if (amount > MAX_TRANSACTION_AMOUNT) {
			throw new InvalidWalletAmountException("1회 거래 금액은 1,000억 원 이하여야 합니다.");
		}
	}

	public static void validateBalanceAmount(long amount) {
		if (amount > MAX_BALANCE) {
			throw new InvalidWalletAmountException("지갑 및 경매 금액은 1조 원 이하여야 합니다.");
		}
	}
}
