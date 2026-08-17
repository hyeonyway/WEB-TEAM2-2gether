package com.dbidding.account.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;

class AccountAdminAuthorizationTest {

	@Test
	void 관리자는_통과한다() {
		AccountRepository accounts = Mockito.mock(AccountRepository.class);
		Account admin = Mockito.mock(Account.class);
		given(admin.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findById(1)).willReturn(Optional.of(admin));

		new AccountAdminAuthorization(accounts).requireAdmin(1);
	}

	@Test
	void 일반_사용자는_거부된다() {
		AccountRepository accounts = Mockito.mock(AccountRepository.class);
		Account user = Mockito.mock(Account.class);
		given(user.getRole()).willReturn(AccountRole.USER);
		given(accounts.findById(1)).willReturn(Optional.of(user));

		assertThatThrownBy(() -> new AccountAdminAuthorization(accounts).requireAdmin(1))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}

	@Test
	void 존재하지_않는_계정은_거부된다() {
		AccountRepository accounts = Mockito.mock(AccountRepository.class);
		given(accounts.findById(1)).willReturn(Optional.empty());

		assertThatThrownBy(() -> new AccountAdminAuthorization(accounts).requireAdmin(1))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}
}
