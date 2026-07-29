package com.dbidding.wallet.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dbidding.global.security.CurrentUser;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletBalanceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

	private final WalletBalanceService walletBalanceService;

	@GetMapping
	public WalletBalanceResponse getBalance(@CurrentUser Integer userId) {
		return walletBalanceService.getBalance(userId);
	}
}
