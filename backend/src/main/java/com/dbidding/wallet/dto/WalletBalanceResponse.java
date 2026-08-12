package com.dbidding.wallet.dto;

public record WalletBalanceResponse(
	long totalBalance,
	long frozenBalance,
	long availableBalance,
	long walletVersion
) {
	public WalletBalanceResponse(long totalBalance, long frozenBalance, long availableBalance) {
		this(totalBalance, frozenBalance, availableBalance, 0L);
	}
}
