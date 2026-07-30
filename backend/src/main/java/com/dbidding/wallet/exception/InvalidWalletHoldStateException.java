package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class InvalidWalletHoldStateException extends RuntimeException {

	public InvalidWalletHoldStateException() {
		super("입찰 금액 동결 상태가 올바르지 않습니다.");
	}
}
