package com.dbidding.account.admin;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.event.AccountSuspendedEvent;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.exception.InvalidAdminTargetException;
import com.dbidding.account.repository.AccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountSuspensionService {

	private final AccountRepository accountRepository;
	private final AccountAdminAuthorization authorization;
	private final ApplicationEventPublisher eventPublisher;

	@Transactional
	public void suspend(Integer actorId, Integer targetId) {
		authorization.requireAdmin(actorId);
		if (actorId.equals(targetId)) {
			throw new InvalidAdminTargetException("자기 자신의 계정은 정지할 수 없습니다.");
		}
		Account target = findTargetForUpdate(targetId);
		requireNonAdminTarget(target);
		suspendTarget(target);
	}

	@Transactional
	public void activate(Integer actorId, Integer targetId) {
		authorization.requireAdmin(actorId);
		if (actorId.equals(targetId)) {
			throw new InvalidAdminTargetException("자기 자신의 계정은 활성화할 수 없습니다.");
		}
		Account target = findTargetForUpdate(targetId);
		requireNonAdminTarget(target);
		if (target.getStatus() == AccountStatus.SUSPENDED) {
			target.activate();
		}
	}

	@Transactional
	public void suspendAutomatically(Integer targetId) {
		suspendTarget(findTargetForUpdate(targetId));
	}

	private void suspendTarget(Account target) {
		if (target.getStatus() != AccountStatus.SUSPENDED) {
			target.suspend();
		}
		eventPublisher.publishEvent(new AccountSuspendedEvent(target.getId()));
	}

	private Account findTargetForUpdate(Integer targetId) {
		return accountRepository.findByIdForUpdate(targetId)
			.orElseThrow(AccountNotFoundException::new);
	}

	private void requireNonAdminTarget(Account target) {
		if (target.getRole() == AccountRole.ADMIN) {
			throw new InvalidAdminTargetException("관리자 계정은 대상으로 지정할 수 없습니다.");
		}
	}
}
