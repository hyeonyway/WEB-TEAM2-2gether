package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InvalidWalletHoldStateException extends ApiException {

	public InvalidWalletHoldStateException() {
		super(
			HttpStatus.CONFLICT,
			"INVALID_WALLET_HOLD_STATE",
			"입찰 금액 동결 상태가 올바르지 않습니다."
		);
	}
}
