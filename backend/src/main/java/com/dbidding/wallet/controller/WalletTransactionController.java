package com.dbidding.wallet.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.wallet.dto.WalletErrorResponse;
import com.dbidding.wallet.dto.WalletTransactionRequest;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.service.WalletService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletTransactionController {

	private final WalletService walletService;

	@PostMapping("/charges")
	public WalletTransactionResponse charge(
		@CurrentUser Integer userId,
		@RequestHeader("Idempotency-Key")
		@NotBlank @Size(max = 64) String idempotencyKey,
		@Valid @RequestBody WalletTransactionRequest request
	) {
		return walletService.charge(
			userId,
			request.amount(),
			idempotencyKey
		);
	}

	@PostMapping("/refunds")
	public WalletTransactionResponse refund(
		@CurrentUser Integer userId,
		@RequestHeader("Idempotency-Key")
		@NotBlank @Size(max = 64) String idempotencyKey,
		@Valid @RequestBody WalletTransactionRequest request
	) {
		return walletService.refund(
			userId,
			request.amount(),
			idempotencyKey
		);
	}

	@ExceptionHandler(InsufficientAvailableBalanceException.class)
	public ResponseEntity<WalletErrorResponse> handleInsufficientAvailableBalance(
		InsufficientAvailableBalanceException exception
	) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new WalletErrorResponse(
			"INSUFFICIENT_AVAILABLE_BALANCE",
			exception.getMessage()
		));
	}

	@ExceptionHandler(IdempotencyConflictException.class)
	public ResponseEntity<WalletErrorResponse> handleIdempotencyConflict(
		IdempotencyConflictException exception
	) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new WalletErrorResponse(
			"IDEMPOTENCY_CONFLICT",
			exception.getMessage()
		));
	}
}
