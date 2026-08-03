package com.dbidding.auction.adapter;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.dbidding.auction.port.WalletPort;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class AuctionWalletAdapter implements WalletPort {

	private final WalletService walletService;

	@Override
	public WalletSnapshot getWallet(Integer userId) {
		return snapshot(walletService.getBalance(userId));
	}

	@Override
	public WalletSnapshot holdBidAmount(
		Integer userId,
		Integer auctionId,
		long totalAmount
	) {
		return snapshot(walletService.hold(userId, auctionId, totalAmount));
	}

	@Override
	public WalletSnapshot releaseBidHold(Integer userId, Integer auctionId) {
		return snapshot(walletService.release(userId, auctionId));
	}

	@Override
	public WalletSnapshot confirmWinningBid(
		Integer userId,
		Integer auctionId,
		long amount
	) {
		return snapshot(walletService.capture(userId, auctionId, amount));
	}

	private WalletSnapshot snapshot(WalletBalanceResponse balance) {
		return new WalletSnapshot(
			balance.availableBalance(),
			balance.frozenBalance()
		);
	}
}
