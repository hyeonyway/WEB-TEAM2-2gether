package com.dbidding.wallet.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletBalanceService {

	private final WalletRepository walletRepository;

	@Transactional(readOnly = true)
	public WalletBalanceResponse getBalance(Integer userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
			.orElseThrow(WalletNotFoundException::new);
		long totalBalance = wallet.getPoint();
		long frozenBalance = walletRepository.sumHeldAmount(wallet.getId());
		if (frozenBalance > totalBalance) {
			throw new InvalidWalletBalanceException();
		}
		return new WalletBalanceResponse(
			totalBalance,
			frozenBalance,
			totalBalance - frozenBalance
		);
	}
}
