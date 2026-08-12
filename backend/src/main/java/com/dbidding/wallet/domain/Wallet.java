package com.dbidding.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "wallets")
public class Wallet {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "user_id", nullable = false, unique = true)
	private Integer userId;

	@Column(nullable = false)
	private long point;

	@Column(name = "projection_version", nullable = false)
	private long projectionVersion;

	private Wallet(Integer userId, long point) {
		if (userId == null) {
			throw new IllegalArgumentException("User ID cannot be null");
		}
		this.userId = userId;
		this.point = point;
	}

	public static Wallet open(Integer userId) {
		return new Wallet(userId, 0L);
	}

	public void credit(long amount) {
		validatePositive(amount);
		point = Math.addExact(point, amount);
	}

	public void debit(long amount) {
		validatePositive(amount);
		if (point < amount) {
			throw new IllegalStateException("Insufficient wallet balance");
		}
		point -= amount;
	}

	/**
	 * 지갑 행 잠금 아래에서만 호출한다. 직접 지갑 변경도 Redis projection과 같은
	 * 단조 버전을 사용해, 서버 재시작 뒤에도 SSE snapshot의 선후 관계를 보장한다.
	 */
	public long advanceProjectionVersion() {
		return ++projectionVersion;
	}

	private void validatePositive(long amount) {
		if (amount <= 0) {
			throw new IllegalArgumentException("Amount must be positive");
		}
	}
}
