package com.dbidding.user.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.dbidding.auth.exception.DuplicateEmailException;
import com.dbidding.auth.exception.DuplicateNicknameException;
import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountRole;
import com.dbidding.user.domain.User;
import com.dbidding.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class UserAccountAdapterTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserAccountAdapter userAccountAdapter;

	@Test
	void 사용자_생성_결과를_Auth_계약으로_변환한다() {
		given(userRepository.saveAndFlush(any(User.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		UserAccount account = userAccountAdapter.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		);

		assertThat(account.email()).isEqualTo("collector@example.com");
		assertThat(account.nickname()).isEqualTo("collector");
		assertThat(account.role()).isEqualTo(UserAccountRole.USER);
		assertThat(account.status()).isEqualTo("ACTIVE");
		assertThat(account.encryptedPassword()).isEqualTo("a".repeat(64));
		assertThat(account.salt()).isEqualTo("b".repeat(32));
	}

	@Test
	void 이메일_UNIQUE_제약이_충돌하면_중복_이메일_예외로_변환한다() {
		DataIntegrityViolationException duplicateEmail =
			dataIntegrityViolation("users.uk_users_email");
		given(userRepository.saveAndFlush(any(User.class))).willThrow(duplicateEmail);

		assertThatThrownBy(() -> createUser())
			.isInstanceOf(DuplicateEmailException.class)
			.hasCause(duplicateEmail);
	}

	@Test
	void 닉네임_UNIQUE_제약이_충돌하면_중복_닉네임_예외로_변환한다() {
		DataIntegrityViolationException duplicateNickname =
			dataIntegrityViolation("`uk_users_nickname`");
		given(userRepository.saveAndFlush(any(User.class))).willThrow(duplicateNickname);

		assertThatThrownBy(() -> createUser())
			.isInstanceOf(DuplicateNicknameException.class)
			.hasCause(duplicateNickname);
	}

	@Test
	void UNIQUE가_아닌_무결성_예외는_그대로_전파한다() {
		DataIntegrityViolationException unrelatedConstraint =
			dataIntegrityViolation("fk_users_unrelated");
		given(userRepository.saveAndFlush(any(User.class))).willThrow(unrelatedConstraint);

		Throwable thrown = catchThrowable(this::createUser);

		assertThat(thrown).isSameAs(unrelatedConstraint);
	}

	@Test
	void 이메일로_조회한_사용자를_Auth_계약으로_변환한다() {
		User user = User.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		);
		given(userRepository.findByEmail("collector@example.com")).willReturn(Optional.of(user));

		Optional<UserAccount> account = userAccountAdapter.findByEmail("collector@example.com");

		assertThat(account)
			.isPresent()
			.get()
			.extracting(UserAccount::role)
			.isEqualTo(UserAccountRole.USER);
	}

	private UserAccount createUser() {
		return userAccountAdapter.create(
			"collector@example.com",
			"collector",
			"a".repeat(64),
			"b".repeat(32)
		);
	}

	private DataIntegrityViolationException dataIntegrityViolation(String constraintName) {
		ConstraintViolationException constraintViolation = new ConstraintViolationException(
			"constraint violation",
			new SQLException("constraint violation"),
			constraintName
		);
		return new DataIntegrityViolationException("data integrity violation", constraintViolation);
	}
}
