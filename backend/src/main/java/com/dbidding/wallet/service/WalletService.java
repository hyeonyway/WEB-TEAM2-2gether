package com.dbidding.wallet.service;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.wallet.domain.HoldStatus;
import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.domain.WalletHold;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidIdempotencyKeyException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.InvalidWalletBalanceException;
import com.dbidding.wallet.exception.InvalidWalletHoldStateException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletService {

	private static final long MINIMUM_CHARGE_AMOUNT = 1_000L;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

	private final WalletRepository walletRepository;
	private final PointRecordRepository pointRecordRepository;
	private final WalletHoldRepository walletHoldRepository;

	@Transactional(readOnly = true)
	public WalletBalanceResponse getBalance(Integer userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
			.orElseThrow(WalletNotFoundException::new);
		return balance(wallet, walletRepository.sumHeldAmount(wallet.getId()));
	}

	@Transactional
	public WalletTransactionResponse charge(Integer userId, long amount, String idempotencyKey) {
		validateChargeAmount(amount);
		validateIdempotencyKey(idempotencyKey);
		Wallet wallet = lockWallet(userId);
		Optional<PointRecord> existing = findReplay(wallet, idempotencyKey);
		if (existing.isPresent()) {
			return replayOrThrow(existing.get(), PointTransactionType.CHARGE, amount);
		}

		wallet.credit(amount);
		PointRecord record = pointRecordRepository.save(
			PointRecord.charge(wallet.getId(), amount, wallet.getPoint(), idempotencyKey)
		);
		return WalletTransactionResponse.from(record);
	}

	@Transactional
	public WalletTransactionResponse refund(Integer userId, long amount, String idempotencyKey) {
		validatePositive(amount);
		validateIdempotencyKey(idempotencyKey);
		Wallet wallet = lockWallet(userId);
		Optional<PointRecord> existing = findReplay(wallet, idempotencyKey);
		if (existing.isPresent()) {
			return replayOrThrow(existing.get(), PointTransactionType.REFUND, -amount);
		}

		long available = wallet.getPoint() - walletRepository.sumHeldAmount(wallet.getId());
		if (available < amount) {
			throw new InsufficientAvailableBalanceException();
		}
		wallet.debit(amount);
		PointRecord record = pointRecordRepository.save(
			PointRecord.refund(wallet.getId(), amount, wallet.getPoint(), idempotencyKey)
		);
		return WalletTransactionResponse.from(record);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse hold(Integer userId, Integer auctionId, long totalAmount) {
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
		long availableBefore = balance(wallet, frozenBefore).availableBalance();
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
		return balance(wallet, Math.addExact(frozenBefore, additionalAmount));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse release(Integer userId, Integer auctionId) {
		Wallet wallet = lockWallet(userId);
		long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());
		Optional<WalletHold> latest = latestHold(wallet.getId(), auctionId);
		long releasedAmount = latest.filter(WalletHold::isHeld)
			.map(WalletHold::getAmount)
			.orElse(0L);
		latest.filter(WalletHold::isHeld)
			.ifPresent(hold -> hold.release(Instant.now()));
		return balance(wallet, Math.subtractExact(frozenBefore, releasedAmount));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse capture(Integer userId, Integer auctionId, long amount) {
		Wallet wallet = lockWallet(userId);
		long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());
		WalletHold hold = latestHold(wallet.getId(), auctionId)
			.orElseThrow(InvalidWalletHoldStateException::new);
		if (hold.getStatus() == HoldStatus.CAPTURED) {
			if (hold.getAmount() != amount) {
				throw new InvalidWalletHoldStateException();
			}
			return balance(wallet, frozenBefore);
		}
		if (!hold.isHeld() || hold.getAmount() != amount || frozenBefore < amount) {
			throw new InvalidWalletHoldStateException();
		}
		balance(wallet, frozenBefore);

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
		return balance(wallet, Math.subtractExact(frozenBefore, amount));
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

	private Optional<PointRecord> findReplay(Wallet wallet, String idempotencyKey) {
		return pointRecordRepository.findByWalletIdAndIdempotencyKey(
			wallet.getId(),
			idempotencyKey
		);
	}

	private WalletTransactionResponse replayOrThrow(
		PointRecord record,
		PointTransactionType transactionType,
		long signedAmount
	) {
		if (record.getTransactionType() != transactionType || record.getAmount() != signedAmount) {
			throw new IdempotencyConflictException();
		}
		return WalletTransactionResponse.from(record);
	}

	private WalletBalanceResponse balance(Wallet wallet, long frozenBalance) {
		if (frozenBalance < 0 || frozenBalance > wallet.getPoint()) {
			throw new InvalidWalletBalanceException();
		}
		return new WalletBalanceResponse(
			wallet.getPoint(),
			frozenBalance,
			wallet.getPoint() - frozenBalance
		);
	}

	private void validateChargeAmount(long amount) {
		if (amount < MINIMUM_CHARGE_AMOUNT) {
			throw new InvalidWalletAmountException("충전 금액은 1,000원 이상이어야 합니다.");
		}
	}

	private void validatePositive(long amount) {
		if (amount <= 0) {
			throw new InvalidWalletAmountException("환불 금액은 0원보다 커야 합니다.");
		}
	}

	private void validateIdempotencyKey(String idempotencyKey) {
		if (idempotencyKey == null
			|| idempotencyKey.isBlank()
			|| idempotencyKey.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
			throw new InvalidIdempotencyKeyException();
		}
	}
}
