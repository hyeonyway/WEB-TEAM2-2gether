package com.dbidding.wallet.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.dto.WalletErrorResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

	private final WalletService walletService;

	@GetMapping
	public WalletBalanceResponse getBalance(@CurrentUser Integer userId) {
		return walletService.getBalance(userId);
	}

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
