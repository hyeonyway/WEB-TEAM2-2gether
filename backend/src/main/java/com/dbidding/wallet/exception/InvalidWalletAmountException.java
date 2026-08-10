package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InvalidWalletAmountException extends ApiException {

	public InvalidWalletAmountException(String message) {
		super(HttpStatus.BAD_REQUEST, "INVALID_WALLET_AMOUNT", message);
	}
}
