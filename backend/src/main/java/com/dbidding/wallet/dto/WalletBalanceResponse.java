package com.dbidding.wallet.dto;

public record WalletBalanceResponse(
	long totalBalance,
	long frozenBalance,
	long availableBalance
) {
}
