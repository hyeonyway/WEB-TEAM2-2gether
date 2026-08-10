package com.dbidding.account.password;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

	private final PasswordHasher passwordHasher = new PasswordHasher();
	private static final String K6_PASSWORD = "K6LoadTest123!";
	private static final String K6_SALT = "6b362d6c6f61642d746573742d73616c";
	private static final String K6_HASH_WITH_DEMO_COST = "9bf31158e6621e360af2186721ceb7337300ae425e0bfd587042165af6ec6ce7";

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

	@Test
	void k6_시드_계정의_데모용_100회_PBKDF2_해시를_검증한다() {
		assertThat(passwordHasher.matches(K6_PASSWORD, K6_SALT, K6_HASH_WITH_DEMO_COST)).isTrue();
	}
}
