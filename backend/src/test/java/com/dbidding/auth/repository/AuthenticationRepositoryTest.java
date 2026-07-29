package com.dbidding.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

import com.dbidding.auth.domain.Authentication;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AuthenticationRepositoryTest {

	@Autowired
	private AuthenticationRepository authenticationRepository;

	@Autowired
	private UserRepository userRepository;

	private Integer userId;

	@BeforeEach
	void setUp() {
		User user = userRepository.saveAndFlush(User.create(
			"auth@example.com",
			"auth-user",
			"a".repeat(64),
			"b".repeat(32)
		));
		userId = user.getId();

		authenticationRepository.saveAndFlush(Authentication.issue(userId, "c".repeat(64)));
	}

	@Test
	void 사용자_ID와_Refresh_Token_hash로_인증_정보를_조회한다() {
		assertThat(authenticationRepository.findByUserId(userId))
			.isPresent()
			.get()
			.extracting(Authentication::getRefreshTokenHash)
			.isEqualTo("c".repeat(64));

		assertThat(authenticationRepository.findByRefreshTokenHash("c".repeat(64)))
			.isPresent();
	}

	@Test
	void 사용자_ID로_인증_정보를_삭제한다() {
		authenticationRepository.deleteByUserId(userId);
		authenticationRepository.flush();

		assertThat(authenticationRepository.findByUserId(userId)).isEmpty();
	}

	@Test
	void 일치하는_refresh_token_hash로만_인증_정보를_삭제한다() {
		int deleted = authenticationRepository.deleteByRefreshTokenHash("c".repeat(64));
		authenticationRepository.flush();

		assertThat(deleted).isEqualTo(1);
		assertThat(authenticationRepository.findByUserId(userId)).isEmpty();
	}

	@Test
	void refresh_token_hash가_다르면_인증_정보를_삭제하지_않는다() {
		int deleted = authenticationRepository.deleteByRefreshTokenHash("d".repeat(64));
		authenticationRepository.flush();

		assertThat(deleted).isZero();
		assertThat(authenticationRepository.findByUserId(userId)).isPresent();
	}
}
