package com.dbidding.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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

	protected Wallet() {
	}

	private Wallet(Integer userId, long point) {
		this.userId = userId;
		this.point = point;
	}

	public static Wallet open(Integer userId) {
		return new Wallet(userId, 0L);
	}

	public Integer getUserId() {
		return userId;
	}

	public long getPoint() {
		return point;
	}
}
