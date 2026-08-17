package com.dbidding.account.warning;

import com.dbidding.account.admin.AccountAdminAccessDeniedException;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminWarningService {

	private final AccountRepository accountRepository;
	private final UserWarningIssuer userWarningIssuer;

	public void warn(Integer actorId, Integer targetId) {
		requireAdmin(actorId);
		userWarningIssuer.issue(targetId, null, UserWarningReason.ADMIN_MANUAL);
	}

	private void requireAdmin(Integer actorId) {
		boolean admin = accountRepository.findById(actorId)
			.map(account -> account.getRole() == AccountRole.ADMIN)
			.orElse(false);
		if (!admin) {
			throw new AccountAdminAccessDeniedException();
		}
	}
}
