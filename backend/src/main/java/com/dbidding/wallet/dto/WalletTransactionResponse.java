package com.dbidding.wallet.dto;

import com.dbidding.wallet.domain.PointRecord;

public record WalletTransactionResponse(
	Long transactionId,
	String transactionType,
	long amount,
	long balance
) {

	public static WalletTransactionResponse from(PointRecord record) {
		return new WalletTransactionResponse(
			record.getId(),
			record.getTransactionType().name(),
			record.getAmount(),
			record.getBalance()
		);
	}
}
