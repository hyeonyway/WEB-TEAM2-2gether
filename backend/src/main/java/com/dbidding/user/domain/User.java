package com.dbidding.user.domain;

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

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@Column(nullable = false, unique = true, length = 255)
	private String email;

	@Column(nullable = false, unique = true, length = 30)
	private String nickname;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserRole role;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private UserStatus status;

	@Column(name = "encrypted_password", nullable = false, length = 64)
	private String encryptedPassword;

	@Column(nullable = false, length = 32)
	private String salt;

	private User(
		String email,
		String nickname,
		UserRole role,
		UserStatus status,
		String encryptedPassword,
		String salt
	) {
		this.email = email;
		this.nickname = nickname;
		this.role = role;
		this.status = status;
		this.encryptedPassword = encryptedPassword;
		this.salt = salt;
	}

	public static User create(String email, String nickname, String encryptedPassword, String salt) {
		return new User(
			email,
			nickname,
			UserRole.USER,
			UserStatus.ACTIVE,
			encryptedPassword,
			salt
		);
	}

}
