package com.dbidding.account.admin;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.event.AccountSuspendedEvent;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.exception.InvalidAdminTargetException;
import com.dbidding.account.repository.AccountRepository;

class AccountSuspensionServiceTest {

	private final AccountRepository accounts = Mockito.mock(AccountRepository.class);
	private final AccountAdminAuthorization authorization = Mockito.mock(AccountAdminAuthorization.class);
	private final ApplicationEventPublisher eventPublisher = Mockito.mock(ApplicationEventPublisher.class);
	private final AccountSuspensionService service = new AccountSuspensionService(accounts, authorization, eventPublisher);

	@Test
	void 관리자가_활성_계정을_정지하고_정지_이벤트를_발행한다() {
		Account target = Mockito.mock(Account.class);
		given(target.getId()).willReturn(2);
		given(target.getRole()).willReturn(AccountRole.USER);
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		service.suspend(1, 2);

		verify(authorization).requireAdmin(1);
		verify(target).suspend();
		verify(eventPublisher).publishEvent(new AccountSuspendedEvent(2));
	}

	@Test
	void 일반_사용자는_계정을_정지할_수_없다() {
		Mockito.doThrow(new AccountAdminAccessDeniedException()).when(authorization).requireAdmin(1);

		assertThatThrownBy(() -> service.suspend(1, 2))
			.isInstanceOf(AccountAdminAccessDeniedException.class);
	}

	@Test
	void 자기_자신을_정지할_수_없다() {
		assertThatThrownBy(() -> service.suspend(1, 1))
			.isInstanceOf(InvalidAdminTargetException.class);
	}

	@Test
	void 관리자_계정은_정지_대상으로_지정할_수_없다() {
		Account target = Mockito.mock(Account.class);
		given(target.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		assertThatThrownBy(() -> service.suspend(1, 2))
			.isInstanceOf(InvalidAdminTargetException.class);
		verify(target, never()).suspend();
	}

	@Test
	void 이미_정지된_계정도_멱등적으로_이벤트를_발행한다() {
		Account target = Mockito.mock(Account.class);
		given(target.getId()).willReturn(2);
		given(target.getRole()).willReturn(AccountRole.USER);
		given(target.getStatus()).willReturn(AccountStatus.SUSPENDED);
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		service.suspend(1, 2);

		verify(target, never()).suspend();
		verify(eventPublisher).publishEvent(new AccountSuspendedEvent(2));
	}

	@Test
	void 존재하지_않는_대상_계정은_찾을_수_없음으로_처리한다() {
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.empty());

		assertThatThrownBy(() -> service.suspend(1, 2))
			.isInstanceOf(AccountNotFoundException.class);
	}

	@Test
	void 관리자가_정지된_계정을_활성화한다() {
		Account target = Mockito.mock(Account.class);
		given(target.getRole()).willReturn(AccountRole.USER);
		given(target.getStatus()).willReturn(AccountStatus.SUSPENDED);
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		service.activate(1, 2);

		verify(authorization).requireAdmin(1);
		verify(target).activate();
	}

	@Test
	void 자기_자신을_활성화할_수_없다() {
		assertThatThrownBy(() -> service.activate(1, 1))
			.isInstanceOf(InvalidAdminTargetException.class);
	}

	@Test
	void 관리자_계정은_활성화_대상으로_지정할_수_없다() {
		Account target = Mockito.mock(Account.class);
		given(target.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		assertThatThrownBy(() -> service.activate(1, 2))
			.isInstanceOf(InvalidAdminTargetException.class);
	}

	@Test
	void 자동_정지는_대상_role_확인_없이_동작한다() {
		Account target = Mockito.mock(Account.class);
		given(target.getId()).willReturn(2);
		given(target.getRole()).willReturn(AccountRole.ADMIN);
		given(accounts.findByIdForUpdate(2)).willReturn(Optional.of(target));

		service.suspendAutomatically(2);

		Mockito.verifyNoInteractions(authorization);
		verify(target).suspend();
		verify(eventPublisher).publishEvent(new AccountSuspendedEvent(2));
	}
}
