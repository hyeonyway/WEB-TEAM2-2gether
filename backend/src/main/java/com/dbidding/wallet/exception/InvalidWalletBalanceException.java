package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InvalidWalletBalanceException extends ApiException {

	public InvalidWalletBalanceException() {
		super(
			HttpStatus.CONFLICT,
			"INVALID_WALLET_BALANCE",
			"지갑 잔액 상태가 올바르지 않습니다."
		);
	}
}
