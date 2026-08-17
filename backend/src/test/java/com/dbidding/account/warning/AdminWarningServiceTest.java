package com.dbidding.account.warning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.dbidding.account.admin.AccountAdminAccessDeniedException;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;

class AdminWarningServiceTest {

	@Test
	void 관리자가_대상에게_주문_없는_수동_경고를_발급한다() {
		AccountRepository accounts = Mockito.mock(AccountRepository.class);
		UserWarningIssuer issuer = Mockito.mock(UserWarningIssuer.class);
		Account admin = Mockito.mock(Account.class);
		given(admin.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findById(1)).willReturn(Optional.of(admin));

		new AdminWarningService(accounts, issuer).warn(1, 2);

		verify(issuer).issue(2, null, UserWarningReason.ADMIN_MANUAL);
	}

	@Test
	void 일반_사용자는_경고를_줄_수_없다() {
		AccountRepository accounts = Mockito.mock(AccountRepository.class);
		UserWarningIssuer issuer = Mockito.mock(UserWarningIssuer.class);
		Account user = Mockito.mock(Account.class);
		given(user.getRole()).willReturn(AccountRole.USER);
		given(accounts.findById(1)).willReturn(Optional.of(user));

		assertThatThrownBy(() -> new AdminWarningService(accounts, issuer).warn(1, 2))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}
}
