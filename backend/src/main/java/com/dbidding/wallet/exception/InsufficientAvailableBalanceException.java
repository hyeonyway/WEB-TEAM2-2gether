package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientAvailableBalanceException extends RuntimeException {

	public InsufficientAvailableBalanceException() {
		super("사용 가능한 잔액이 부족합니다.");
	}
}
