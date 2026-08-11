package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InsufficientAvailableBalanceException extends ApiException {

	public InsufficientAvailableBalanceException() {
		super(
			HttpStatus.CONFLICT,
			"INSUFFICIENT_AVAILABLE_BALANCE",
			"사용 가능한 잔액이 부족합니다."
		);
	}
}
