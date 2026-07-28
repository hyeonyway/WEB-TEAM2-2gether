package com.dbidding.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.dbidding.auth.port.UserAccount;
import com.dbidding.auth.port.UserAccountRole;

@ExtendWith(MockitoExtension.class)
class UserAccountAdapterTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserAccountAdapter userAccountAdapter;

	@Test
	void 사용자_생성_결과를_Auth_계약으로_변환한다() {
		given(userRepository.save(any(User.class)))
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
}
