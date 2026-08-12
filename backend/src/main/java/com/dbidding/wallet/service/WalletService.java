package com.dbidding.wallet.service;

import java.time.Clock;
import java.util.Arrays;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
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
import com.dbidding.wallet.exception.WalletAlreadyExistsException;
import com.dbidding.wallet.exception.WalletNotFoundException;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.metrics.WalletMetrics.Operation;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;

import io.micrometer.core.instrument.Timer;

@Service
@Profile("!redis")
public class WalletService {

	private static final long MINIMUM_CHARGE_AMOUNT = 1_000L;
	private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;
	private static final String USER_ID_UNIQUE_CONSTRAINT = "uk_wallets_user_id";

	private final WalletRepository walletRepository;
	private final PointRecordRepository pointRecordRepository;
	private final WalletHoldRepository walletHoldRepository;
	private final WalletMetrics walletMetrics;
	private final Clock clock;
	private final ApplicationEventPublisher eventPublisher;

	@Autowired
	public WalletService(
		WalletRepository walletRepository,
		PointRecordRepository pointRecordRepository,
		WalletHoldRepository walletHoldRepository,
		WalletMetrics walletMetrics,
		Clock clock,
		ApplicationEventPublisher eventPublisher
	) {
		this.walletRepository = walletRepository;
		this.pointRecordRepository = pointRecordRepository;
		this.walletHoldRepository = walletHoldRepository;
		this.walletMetrics = walletMetrics;
		this.clock = clock;
		this.eventPublisher = eventPublisher;
	}

	/** 기존 단위 테스트와 테스트용 확장 서비스의 생성자 계약을 유지한다. */
	protected WalletService(
		WalletRepository walletRepository,
		PointRecordRepository pointRecordRepository,
		WalletHoldRepository walletHoldRepository,
		WalletMetrics walletMetrics,
		Clock clock
	) {
		this(walletRepository, pointRecordRepository, walletHoldRepository, walletMetrics, clock, event -> { });
	}

	@Transactional(readOnly = true)
	public WalletBalanceResponse getBalance(Integer userId) {
		Wallet wallet = walletRepository.findByUserId(userId)
			.orElseThrow(WalletNotFoundException::new);
		return balance(wallet, walletRepository.sumHeldAmount(wallet.getId()));
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void provision(Integer userId) {
		if (walletRepository.existsByUserId(userId)) {
			throw new WalletAlreadyExistsException();
		}
		try {
			walletRepository.saveAndFlush(Wallet.open(userId));
		} catch (DataIntegrityViolationException exception) {
			if (isUserIdUniqueConstraintViolation(exception)) {
				throw new WalletAlreadyExistsException(exception);
			}
			throw exception;
		}
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
		publishBalanceChanged(wallet, balance(wallet, walletRepository.sumHeldAmount(wallet.getId())));
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
		publishBalanceChanged(wallet, balance(wallet, walletRepository.sumHeldAmount(wallet.getId())));
		return WalletTransactionResponse.from(record);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse hold(Integer userId, Integer auctionId, long totalAmount) {
		return walletMetrics.observe(
			Operation.HOLD,
			() -> holdObserved(userId, auctionId, totalAmount)
		);
	}

	private WalletBalanceResponse holdObserved(Integer userId, Integer auctionId, long totalAmount) {
		Wallet wallet = lockWallet(userId, Operation.HOLD);
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
		WalletBalanceResponse balance = balance(wallet, Math.addExact(frozenBefore, additionalAmount));
		publishBalanceChanged(wallet, balance);
		return balance;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse release(Integer userId, Integer auctionId) {
		return walletMetrics.observe(
			Operation.RELEASE,
			() -> releaseObserved(userId, auctionId)
		);
	}

	private WalletBalanceResponse releaseObserved(Integer userId, Integer auctionId) {
		Wallet wallet = lockWallet(userId, Operation.RELEASE);
		long frozenBefore = walletRepository.sumHeldAmount(wallet.getId());
		Optional<WalletHold> latest = latestHold(wallet.getId(), auctionId);
		long releasedAmount = latest.filter(WalletHold::isHeld)
			.map(WalletHold::getAmount)
			.orElse(0L);
		latest.filter(WalletHold::isHeld)
			.ifPresent(hold -> hold.release(clock.instant()));
		WalletBalanceResponse balance = balance(wallet, Math.subtractExact(frozenBefore, releasedAmount));
		publishBalanceChanged(wallet, balance);
		return balance;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse capture(Integer userId, Integer auctionId, long amount) {
		return walletMetrics.observe(
			Operation.CAPTURE,
			() -> captureObserved(userId, auctionId, amount)
		);
	}

	/**
	 * 즉시 낙찰처럼 {@link #hold(Integer, Integer, long)}가 같은 트랜잭션에서 이미 구매자
	 * 지갑 행을 잠근 경우에만 사용한다. 같은 행에 대한 두 번째 {@code FOR UPDATE}를 피하면서
	 * 예치금 확정을 수행한다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public WalletBalanceResponse captureAfterHold(Integer userId, Integer auctionId, long amount) {
		return walletMetrics.observe(
			Operation.CAPTURE,
			() -> captureObserved(walletForAlreadyLockedTransaction(userId), auctionId, amount)
		);
	}

	/**
	 * 한 트랜잭션에서 여러 지갑을 건드릴 때 사용자 ID 오름차순으로 행 잠금을 먼저 획득한다.
	 * 즉시 낙찰은 기존 최고 입찰자·구매자·판매자를 모두 이 순서로 선점해 교차 대기를 막는다.
	 */
	@Transactional(propagation = Propagation.MANDATORY)
	public void lockWalletsInOrder(Integer... userIds) {
		Arrays.stream(userIds)
			.filter(java.util.Objects::nonNull)
			.distinct()
			.sorted()
			.forEach(this::lockWallet);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletTransactionResponse settle(Integer sellerId, Integer auctionId, long amount) {
		validatePositive(amount);
		Wallet wallet = lockWallet(sellerId);
		wallet.credit(amount);
		PointRecord record = pointRecordRepository.save(
			PointRecord.orderSettlement(wallet.getId(), auctionId, amount, wallet.getPoint())
		);
		publishBalanceChanged(wallet, balance(wallet, walletRepository.sumHeldAmount(wallet.getId())));
		return WalletTransactionResponse.from(record);
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public WalletTransactionResponse cancelRefund(Integer buyerId, Integer auctionId, long amount) {
		validatePositive(amount);
		Wallet wallet = lockWallet(buyerId);
		wallet.credit(amount);
		PointRecord record = pointRecordRepository.save(
			PointRecord.orderCancelRefund(wallet.getId(), auctionId, amount, wallet.getPoint())
		);
		publishBalanceChanged(wallet, balance(wallet, walletRepository.sumHeldAmount(wallet.getId())));
		return WalletTransactionResponse.from(record);
	}

	private WalletBalanceResponse captureObserved(Integer userId, Integer auctionId, long amount) {
		Wallet wallet = lockWallet(userId, Operation.CAPTURE);
		return captureObserved(wallet, auctionId, amount);
	}

	private WalletBalanceResponse captureObserved(Wallet wallet, Integer auctionId, long amount) {
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
		validateFrozenBalance(wallet, frozenBefore);

		wallet.debit(amount);
		hold.capture(clock.instant());
		pointRecordRepository.save(
			PointRecord.auctionCapture(
				wallet.getId(),
				auctionId,
				amount,
				wallet.getPoint()
			)
		);
		WalletBalanceResponse balance = balance(wallet, Math.subtractExact(frozenBefore, amount));
		publishBalanceChanged(wallet, balance);
		return balance;
	}

	private Wallet lockWallet(Integer userId) {
		return walletRepository.findByUserIdForUpdate(userId)
			.orElseThrow(WalletNotFoundException::new);
	}

	private Wallet lockWallet(Integer userId, Operation operation) {
		Timer.Sample sample = walletMetrics.start();
		try {
			return lockWallet(userId);
		} finally {
			walletMetrics.finishLockWait(sample, operation);
		}
	}

	private Wallet walletForAlreadyLockedTransaction(Integer userId) {
		return walletRepository.findByUserId(userId)
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

	private boolean isUserIdUniqueConstraintViolation(Throwable exception) {
		Throwable cause = exception;
		while (cause != null) {
			if (cause instanceof ConstraintViolationException constraintViolation) {
				String constraintName = constraintViolation.getConstraintName();
				if (constraintName == null) {
					return false;
				}
				String normalizedName = constraintName.replace("`", "");
				String unqualifiedName = normalizedName.substring(normalizedName.lastIndexOf('.') + 1);
				return unqualifiedName.equalsIgnoreCase(USER_ID_UNIQUE_CONSTRAINT);
			}
			cause = cause.getCause();
		}
		return false;
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
		validateFrozenBalance(wallet, frozenBalance);
		return new WalletBalanceResponse(
			wallet.getPoint(),
			frozenBalance,
			wallet.getPoint() - frozenBalance,
			wallet.getProjectionVersion()
		);
	}

	private void validateFrozenBalance(Wallet wallet, long frozenBalance) {
		if (frozenBalance < 0 || frozenBalance > wallet.getPoint()) {
			throw new InvalidWalletBalanceException();
		}
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

	/**
	 * DB가 승인 원본인 프로필에서는 projection 버전을 증가시키고 browser SSE를 발행한다.
	 * Redis 승인 프로필의 Stream projection은 Redis가 이미 부여한 버전을 사용하므로 하위
	 * 구현이 이 훅을 비활성화한다.
	 */
	protected void publishBalanceChanged(Wallet wallet, WalletBalanceResponse balance) {
		long walletVersion = wallet.advanceProjectionVersion();
		eventPublisher.publishEvent(new WalletBalanceChangedEvent(wallet.getUserId(), balance,
			walletVersion, clock.instant()));
	}
}
