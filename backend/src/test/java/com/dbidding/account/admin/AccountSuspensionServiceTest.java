package com.dbidding.account.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.repository.AccountRepository;

class AccountSuspensionServiceTest {

	@Test
	void 관리자가_활성_계정을_정지하고_모든_세션을_종료한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		AccountSessionRevoker sessionRevoker = org.mockito.Mockito.mock(AccountSessionRevoker.class);
		Account admin = org.mockito.Mockito.mock(Account.class);
		Account target = org.mockito.Mockito.mock(Account.class);
		given(admin.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findById(1)).willReturn(Optional.of(admin));
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		new AccountSuspensionService(accounts, sessionRevoker).suspend(1, 2);

		verify(target).suspend();
		verify(sessionRevoker).revoke(2);
	}

	@Test
	void 일반_사용자는_계정을_정지할_수_없다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		AccountSessionRevoker sessionRevoker = org.mockito.Mockito.mock(AccountSessionRevoker.class);
		Account user = org.mockito.Mockito.mock(Account.class);
		given(user.getRole()).willReturn(AccountRole.USER);
		given(accounts.findById(1)).willReturn(Optional.of(user));

		assertThatThrownBy(() -> new AccountSuspensionService(accounts, sessionRevoker).suspend(1, 2))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}

	@Test
	void 자기_자신을_정지할_수_없다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		AccountSessionRevoker sessionRevoker = org.mockito.Mockito.mock(AccountSessionRevoker.class);
		Account admin = org.mockito.Mockito.mock(Account.class);
		given(admin.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findById(1)).willReturn(Optional.of(admin));

		assertThatThrownBy(() -> new AccountSuspensionService(accounts, sessionRevoker).suspend(1, 1))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void 이미_정지된_계정도_멱등적으로_세션을_종료한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		AccountSessionRevoker sessionRevoker = org.mockito.Mockito.mock(AccountSessionRevoker.class);
		Account admin = org.mockito.Mockito.mock(Account.class);
		Account target = org.mockito.Mockito.mock(Account.class);
		given(admin.getRole()).willReturn(AccountRole.ADMIN);
		given(target.getStatus()).willReturn(AccountStatus.SUSPENDED);
		given(accounts.findById(1)).willReturn(Optional.of(admin));
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		new AccountSuspensionService(accounts, sessionRevoker).suspend(1, 2);

		verify(target, org.mockito.Mockito.never()).suspend();
		verify(sessionRevoker).revoke(2);
	}

	@Test
	void 존재하지_않는_대상_계정은_찾을_수_없음으로_처리한다() {
		AccountRepository accounts = org.mockito.Mockito.mock(AccountRepository.class);
		AccountSessionRevoker sessionRevoker = org.mockito.Mockito.mock(AccountSessionRevoker.class);
		Account admin = org.mockito.Mockito.mock(Account.class);
		given(admin.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findById(1)).willReturn(Optional.of(admin));
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.empty());

		assertThatThrownBy(() -> new AccountSuspensionService(accounts, sessionRevoker).suspend(1, 2))
			.isInstanceOf(AccountNotFoundException.class);
	}
}
