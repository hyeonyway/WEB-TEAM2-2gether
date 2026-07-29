package com.dbidding.auth.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

	private final PasswordHasher passwordHasher = new PasswordHasher();

	@Test
	void 같은_비밀번호도_서로_다른_salt와_hash를_만든다() {
		PasswordHash first = passwordHasher.hash("Password123!");
		PasswordHash second = passwordHasher.hash("Password123!");

		assertThat(first.salt())
			.hasSize(32)
			.matches("[0-9a-f]{32}")
			.isNotEqualTo(second.salt());
		assertThat(first.encryptedPassword())
			.hasSize(64)
			.matches("[0-9a-f]{64}")
			.isNotEqualTo(second.encryptedPassword());
	}

	@Test
	void 저장된_salt와_hash로_비밀번호_일치_여부를_검증한다() {
		PasswordHash passwordHash = passwordHasher.hash("Password123!");

		assertThat(passwordHasher.matches(
			"Password123!",
			passwordHash.salt(),
			passwordHash.encryptedPassword()
		)).isTrue();
		assertThat(passwordHasher.matches(
			"WrongPassword123!",
			passwordHash.salt(),
			passwordHash.encryptedPassword()
		)).isFalse();
	}
}
