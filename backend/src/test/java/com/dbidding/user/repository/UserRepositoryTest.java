package com.dbidding.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.dbidding.user.domain.User;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		userRepository.saveAndFlush(User.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		));
	}

	@Test
	void 이메일과_닉네임의_중복을_조회한다() {
		assertThat(userRepository.existsByEmail("collector@example.com")).isTrue();
		assertThat(userRepository.existsByNickname("collector")).isTrue();
		assertThat(userRepository.existsByEmail("other@example.com")).isFalse();
		assertThat(userRepository.existsByNickname("other")).isFalse();
	}

	@Test
	void 이메일로_사용자를_조회한다() {
		assertThat(userRepository.findByEmail("collector@example.com"))
			.isPresent()
			.get()
			.extracting(User::getNickname)
			.isEqualTo("collector");
	}
}
