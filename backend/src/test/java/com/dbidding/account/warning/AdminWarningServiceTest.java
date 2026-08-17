package com.dbidding.account.warning;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.dbidding.account.admin.AccountAdminAccessDeniedException;
import com.dbidding.account.admin.AccountAdminAuthorization;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.exception.InvalidAdminTargetException;
import com.dbidding.account.repository.AccountRepository;

class AdminWarningServiceTest {

	private final AccountRepository accounts = Mockito.mock(AccountRepository.class);
	private final AccountAdminAuthorization authorization = Mockito.mock(AccountAdminAuthorization.class);
	private final UserWarningIssuer issuer = Mockito.mock(UserWarningIssuer.class);
	private final AdminWarningService service = new AdminWarningService(accounts, authorization, issuer);

	@Test
	void 관리자가_대상에게_주문_없는_수동_경고를_발급한다() {
		Account target = Mockito.mock(Account.class);
		given(target.getRole()).willReturn(AccountRole.USER);
		given(accounts.findById(2)).willReturn(Optional.of(target));

		service.warn(1, 2);

		verify(authorization).requireAdmin(1);
		verify(issuer).issue(2, null, UserWarningReason.ADMIN_MANUAL);
	}

	@Test
	void 일반_사용자는_경고를_줄_수_없다() {
		Mockito.doThrow(new AccountAdminAccessDeniedException()).when(authorization).requireAdmin(1);

		assertThatThrownBy(() -> service.warn(1, 2))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}

	@Test
	void 관리자_계정에는_경고를_줄_수_없다() {
		Account target = Mockito.mock(Account.class);
		given(target.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findById(1)).willReturn(Optional.of(target));

		assertThatThrownBy(() -> service.warn(1, 1))
			.isInstanceOf(InvalidAdminTargetException.class);
		verify(issuer, never()).issue(Mockito.any(), Mockito.any(), Mockito.any());
	}

	@Test
	void 존재하지_않는_대상은_찾을_수_없음으로_처리한다() {
		given(accounts.findById(2)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.warn(1, 2))
			.isInstanceOf(AccountNotFoundException.class);
	}
}
