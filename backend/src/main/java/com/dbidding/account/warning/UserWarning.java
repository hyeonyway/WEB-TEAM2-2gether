package com.dbidding.account.warning;

import java.time.Instant;

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

@Entity
@Table(name = "user_warnings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserWarning {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false)
	private Integer userId;

	@Column(name = "order_id")
	private Integer orderId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 32)
	private UserWarningReason reason;

	@Column(name = "issued_at", nullable = false, updatable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	private UserWarning(
		Integer userId,
		Integer orderId,
		UserWarningReason reason,
		Instant issuedAt,
		Instant expiresAt
	) {
		this.userId = userId;
		this.orderId = orderId;
		this.reason = reason;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
	}

	public static UserWarning issued(
		Integer userId,
		Integer orderId,
		UserWarningReason reason,
		Instant issuedAt,
		Instant expiresAt
	) {
		return new UserWarning(userId, orderId, reason, issuedAt, expiresAt);
	}
}
