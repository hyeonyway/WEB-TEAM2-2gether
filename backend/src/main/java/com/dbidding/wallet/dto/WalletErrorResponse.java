package com.dbidding.wallet.dto;

public record WalletErrorResponse(
	String code,
	String message
) {
}
