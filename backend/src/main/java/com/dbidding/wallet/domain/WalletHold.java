package com.dbidding.wallet.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.dbidding.wallet.exception.InvalidWalletHoldStateException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "wallet_holds")
public class WalletHold {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "wallet_id", nullable = false)
	private Integer walletId;

	@Column(name = "auction_id", nullable = false)
	private Integer auctionId;

	@Column(nullable = false)
	private long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private HoldStatus status;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "released_at")
	private Instant releasedAt;

	@Column(name = "projection_version", nullable = false)
	private long projectionVersion;

	@Column(name = "event_id")
	private UUID eventId;

	private WalletHold(Integer walletId, Integer auctionId, long amount) {
		if (walletId == null || auctionId == null) {
			throw new IllegalArgumentException("Wallet ID and auction ID cannot be null");
		}
		if (amount <= 0) {
			throw new IllegalArgumentException("Hold amount must be positive");
		}
		this.walletId = walletId;
		this.auctionId = auctionId;
		this.amount = amount;
		this.status = HoldStatus.HELD;
	}

	public static WalletHold held(Integer walletId, Integer auctionId, long amount) {
		return new WalletHold(walletId, auctionId, amount);
	}

	public boolean isHeld() {
		return status == HoldStatus.HELD;
	}

	public void increaseTo(long newAmount) {
		validateHeld();
		if (newAmount < amount) {
			throw new InvalidWalletHoldStateException();
		}
		amount = newAmount;
	}

	public void release(Instant now) {
		transitionTo(HoldStatus.RELEASED, now);
	}

	public void capture(Instant now) {
		transitionTo(HoldStatus.CAPTURED, now);
	}

	private void transitionTo(HoldStatus nextStatus, Instant now) {
		validateHeld();
		if (now == null) {
			throw new IllegalArgumentException("Transition time cannot be null");
		}
		status = nextStatus;
		releasedAt = now;
	}

	private void validateHeld() {
		if (!isHeld()) {
			throw new InvalidWalletHoldStateException();
		}
	}
}
