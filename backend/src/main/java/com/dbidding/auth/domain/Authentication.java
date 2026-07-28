package com.dbidding.auth.domain;

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
@Table(name = "authentication")
public class Authentication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(name = "user_id", nullable = false, unique = true)
	private Integer userId;

	@Column(name = "refresh_token", nullable = false, unique = true, length = 64)
	private String refreshTokenHash;

	private Authentication(Integer userId, String refreshTokenHash) {
		this.userId = userId;
		this.refreshTokenHash = refreshTokenHash;
	}

	public static Authentication issue(Integer userId, String refreshTokenHash) {
		return new Authentication(userId, refreshTokenHash);
	}

	public void rotate(String newRefreshTokenHash) {
		this.refreshTokenHash = newRefreshTokenHash;
	}

}
