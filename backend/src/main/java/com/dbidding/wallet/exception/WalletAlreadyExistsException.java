package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class WalletAlreadyExistsException extends ApiException {

	public WalletAlreadyExistsException() {
		super(HttpStatus.CONFLICT, "WALLET_ALREADY_EXISTS", "이미 지갑이 존재합니다.");
	}

	public WalletAlreadyExistsException(Throwable cause) {
		super(
			HttpStatus.CONFLICT,
			"WALLET_ALREADY_EXISTS",
			"이미 지갑이 존재합니다.",
			cause
		);
	}
}
