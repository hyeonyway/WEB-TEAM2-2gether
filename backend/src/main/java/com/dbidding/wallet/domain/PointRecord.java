package com.dbidding.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "point_records")
public class PointRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "wallet_id", nullable = false)
	private Integer walletId;

	@Column(name = "auction_id")
	private Integer auctionId;

	@Column(nullable = false)
	private long amount;

	@Column(nullable = false)
	private long balance;

	@Enumerated(EnumType.STRING)
	@Column(name = "transaction_type", nullable = false, length = 32)
	private PointTransactionType transactionType;

	@Column(name = "idempotency_key", length = 64)
	private String idempotencyKey;

	@Column(name = "event_id")
	private UUID eventId;

	private PointRecord(
		Integer walletId,
		Integer auctionId,
		long amount,
		long balance,
		PointTransactionType transactionType,
		String idempotencyKey
	) {
		if (walletId == null) {
			throw new IllegalArgumentException("Wallet ID cannot be null");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
		this.walletId = walletId;
		this.auctionId = auctionId;
		this.amount = isCredit(transactionType) ? amount : -amount;
		this.balance = balance;
		this.transactionType = transactionType;
		this.idempotencyKey = idempotencyKey;
	}

	public static PointRecord charge(Integer walletId, long amount, long balance, String idempotencyKey) {
		return new PointRecord(
			walletId,
			null,
			amount,
			balance,
			PointTransactionType.CHARGE,
			idempotencyKey
		);
	}

	public static PointRecord refund(Integer walletId, long amount, long balance, String idempotencyKey) {
		return new PointRecord(
			walletId,
			null,
			amount,
			balance,
			PointTransactionType.REFUND,
			idempotencyKey
		);
	}

	public static PointRecord projected(
		Integer walletId, Integer auctionId, long amount, long balance,
		PointTransactionType transactionType, String idempotencyKey, UUID eventId
	) {
		PointRecord record = new PointRecord(walletId, auctionId, amount, balance, transactionType, idempotencyKey);
		record.eventId = eventId;
		return record;
	}

	public static PointRecord auctionCapture(Integer walletId, Integer auctionId, long amount, long balance) {
		if (auctionId == null) {
			throw new IllegalArgumentException("Auction ID cannot be null");
		}
		return new PointRecord(
			walletId,
			auctionId,
			amount,
			balance,
			PointTransactionType.AUCTION_CAPTURE,
			null
		);
	}

	public static PointRecord orderSettlement(Integer walletId, Integer auctionId, long amount, long balance) {
		return orderCredit(walletId, auctionId, amount, balance, PointTransactionType.ORDER_SETTLEMENT);
	}

	public static PointRecord orderCancelRefund(Integer walletId, Integer auctionId, long amount, long balance) {
		return orderCredit(walletId, auctionId, amount, balance, PointTransactionType.ORDER_CANCEL_REFUND);
	}

	private static PointRecord orderCredit(
		Integer walletId,
		Integer auctionId,
		long amount,
		long balance,
		PointTransactionType transactionType
	) {
		if (auctionId == null) {
			throw new IllegalArgumentException("Auction ID cannot be null");
		}
		return new PointRecord(walletId, auctionId, amount, balance, transactionType, null);
	}

	private static boolean isCredit(PointTransactionType transactionType) {
		return transactionType == PointTransactionType.CHARGE
			|| transactionType == PointTransactionType.ORDER_SETTLEMENT
			|| transactionType == PointTransactionType.ORDER_CANCEL_REFUND;
	}
}
