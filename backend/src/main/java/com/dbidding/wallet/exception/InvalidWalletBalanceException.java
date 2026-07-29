package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class InvalidWalletBalanceException extends RuntimeException {

	public InvalidWalletBalanceException() {
		super("지갑 잔액 상태가 올바르지 않습니다.");
	}
}
