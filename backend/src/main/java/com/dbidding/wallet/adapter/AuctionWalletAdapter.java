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
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.InvalidWalletHoldStateException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Component
@Profile("!auction-mock")
@RequiredArgsConstructor
public class AuctionWalletAdapter implements WalletPort {

	private final WalletRepository walletRepository;
	private final WalletHoldRepository walletHoldRepository;
	private final PointRecordRepository pointRecordRepository;

	@Override
	@Transactional(readOnly = true)
	public WalletSnapshot getWallet(Integer userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
			.orElseThrow(WalletNotFoundException::new);
		return snapshot(wallet, walletRepository.sumHeldAmount(wallet.getId()));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public WalletSnapshot holdBidAmount(
		Integer userId,
		Integer auctionId,
		long totalAmount
	) {
		Wallet wallet = lockWallet(userId);
		long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());
		Optional<WalletHold> latest = latestHold(wallet.getId(), auctionId);
		long currentAmount = latest.filter(WalletHold::isHeld)
			.map(WalletHold::getAmount)
			.orElse(0L);
		if (totalAmount < currentAmount) {
			throw new InvalidWalletHoldStateException();
		}
		long additionalAmount = Math.subtractExact(totalAmount, currentAmount);
		long availableBefore = availableBalance(wallet, frozenBefore);
		if (availableBefore < additionalAmount) {
			throw new InsufficientAvailableBalanceException();
		}

		if (latest.filter(WalletHold::isHeld).isPresent()) {
			latest.orElseThrow().increaseTo(totalAmount);
		} else {
			walletHoldRepository.save(
				WalletHold.held(wallet.getId(), auctionId, totalAmount)
			);
		}
		return snapshot(wallet, Math.addExact(frozenBefore, additionalAmount));
	}

	@Override
	@Transactional(propagation = Propagation.MANDATORY)
	public WalletSnapshot releaseBidHold(Integer userId, Integer auctionId) {
		Wallet wallet = lockWallet(userId);
		long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());
		Optional<WalletHold> latest = latestHold(wallet.getId(), auctionId);
		long releasedAmount = latest.filter(WalletHold::isHeld)
			.map(WalletHold::getAmount)
			.orElse(0L);
		latest.filter(WalletHold::isHeld)
			.ifPresent(hold -> hold.release(Instant.now()));
		return snapshot(wallet, Math.subtractExact(frozenBefore, releasedAmount));
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

	private long availableBalance(Wallet wallet, long frozenBalance) {
		if (frozenBalance < 0 || frozenBalance > wallet.getPoint()) {
			throw new InvalidWalletBalanceException();
		}
		return wallet.getPoint() - frozenBalance;
	}
}
