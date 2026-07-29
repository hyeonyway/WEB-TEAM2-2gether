package com.dbidding.wallet.service;

import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.dbidding.wallet.domain.PointRecord;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.IdempotencyConflictException;
import com.dbidding.wallet.exception.InsufficientAvailableBalanceException;
import com.dbidding.wallet.exception.InvalidIdempotencyKeyException;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WalletTransactionService {

	private static final long MINIMUM_CHARGE_AMOUNT = 1_000L;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

	private final WalletRepository walletRepository;
	private final PointRecordRepository pointRecordRepository;

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

	private Wallet lockWallet(Integer userId) {
		return walletRepository.findByUserIdForUpdate(userId)
			.orElseThrow(WalletNotFoundException::new);
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
