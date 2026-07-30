package com.dbidding.wallet.adapter;

import java.time.Instant;
import java.util.Optional;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.auction.port.WalletPort;
import com.dbidding.wallet.domain.HoldStatus;
import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.InvalidWalletHoldStateException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.service.WalletService;

import lombok.RequiredArgsConstructor;

@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class AuctionWalletAdapter implements WalletPort {

	private final WalletService walletService;
	private final WalletRepository walletRepository;
	private final WalletHoldRepository walletHoldRepository;
	private final PointRecordRepository pointRecordRepository;

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
	@Transactional(propagation = Propagation.MANDATORY)
	public WalletSnapshot confirmWinningBid(
		Integer userId,
		Integer auctionId,
		long amount
	) {
		Wallet wallet = lockWallet(userId);
		long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());
		WalletHold hold = latestHold(wallet.getId(), auctionId)
			.orElseThrow(InvalidWalletHoldStateException::new);
		if (hold.getStatus() == HoldStatus.CAPTURED) {
			if (hold.getAmount() != amount) {
				throw new InvalidWalletHoldStateException();
			}
			return snapshot(wallet, frozenBefore);
		}
		if (!hold.isHeld() || hold.getAmount() != amount || frozenBefore < amount) {
			throw new InvalidWalletHoldStateException();
		}
		availableBalance(wallet, frozenBefore);

		wallet.debit(amount);
		hold.capture(Instant.now());
		pointRecordRepository.save(
			PointRecord.auctionCapture(
				wallet.getId(),
				auctionId,
				amount,
				wallet.getPoint()
			)
		);
		return snapshot(wallet, frozenBefore - amount);
	}

	private Wallet lockWallet(Integer userId) {
		return walletRepository.findByUserIdForUpdate(userId)
			.orElseThrow(WalletNotFoundException::new);
	}

	private Optional<WalletHold> latestHold(Integer walletId, Integer auctionId) {
		return walletHoldRepository.findFirstByWalletIdAndAuctionIdOrderByIdDesc(
			walletId,
			auctionId
		);
	}

	private WalletSnapshot snapshot(Wallet wallet, long frozenBalance) {
		return new WalletSnapshot(
			availableBalance(wallet, frozenBalance),
			frozenBalance
		);
	}

	private WalletSnapshot snapshot(WalletBalanceResponse balance) {
		return new WalletSnapshot(
			balance.availableBalance(),
			balance.frozenBalance()
		);
	}

	private long availableBalance(Wallet wallet, long frozenBalance) {
		if (frozenBalance < 0 || frozenBalance > wallet.getPoint()) {
			throw new InvalidWalletBalanceException();
		}
		return wallet.getPoint() - frozenBalance;
	}
}
