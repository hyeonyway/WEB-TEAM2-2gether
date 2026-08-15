package com.dbidding.account.admin;

import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.domain.AccountStatus;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.repository.AccountRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountSuspensionService {

	private final AccountRepository accountRepository;
	private final AccountSessionRevoker sessionRevoker;

	@Transactional
	public void suspend(Integer actorId, Integer targetId) {
		requireAdmin(actorId);
		if (actorId.equals(targetId)) {
			throw new IllegalArgumentException("자기 자신의 계정은 정지할 수 없습니다.");
		}
		suspendTarget(targetId);
	}

	@Transactional
	public void activate(Integer actorId, Integer targetId) {
		requireAdmin(actorId);
		if (actorId.equals(targetId)) {
			throw new IllegalArgumentException("자기 자신의 계정은 활성화할 수 없습니다.");
		}
		Account target = accountRepository.findById(targetId)
			.orElseThrow(AccountNotFoundException::new);
		if (target.getStatus() == AccountStatus.SUSPENDED) {
			target.activate();
		}
	}

	@Transactional
	public void suspendAutomatically(Integer targetId) {
		suspendTarget(targetId);
	}

	private void suspendTarget(Integer targetId) {
		Account target = accountRepository.findByIdForUpdate(targetId)
			.orElseThrow(AccountNotFoundException::new);
		if (target.getStatus() != AccountStatus.SUSPENDED) {
			target.suspend();
		}
		sessionRevoker.revoke(targetId);
	}

	private void requireAdmin(Integer actorId) {
		boolean admin = accountRepository.findById(actorId)
			.map(account -> account.getRole() == AccountRole.ADMIN)
			.orElse(false);
		if (!admin) throw new AccountAdminAccessDeniedException();
	}
}
