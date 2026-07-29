package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidWalletAmountException extends RuntimeException {

	public InvalidWalletAmountException(String message) {
		super(message);
	}
}
