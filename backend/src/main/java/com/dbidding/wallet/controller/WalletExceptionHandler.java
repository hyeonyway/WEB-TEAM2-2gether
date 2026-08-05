package com.dbidding.wallet.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.dbidding.wallet.dto.WalletErrorResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;

@RestControllerAdvice
public class WalletExceptionHandler {

	@ExceptionHandler(InvalidWalletBalanceException.class)
	public ResponseEntity<WalletErrorResponse> handleInvalidWalletBalance(
		InvalidWalletBalanceException exception
	) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new WalletErrorResponse(
			"INVALID_WALLET_BALANCE",
			exception.getMessage()
		));
	}
}
