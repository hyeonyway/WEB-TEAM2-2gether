package com.dbidding.account.warning;

import com.dbidding.account.admin.AccountAdminAuthorization;
import com.dbidding.account.domain.Account;
import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.exception.AccountNotFoundException;
import com.dbidding.account.exception.InvalidAdminTargetException;
import com.dbidding.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminWarningService {

	private final AccountRepository accountRepository;
	private final AccountAdminAuthorization authorization;
	private final UserWarningIssuer userWarningIssuer;

	public void warn(Integer actorId, Integer targetId) {
		authorization.requireAdmin(actorId);
		Account target = accountRepository.findById(targetId)
			.orElseThrow(AccountNotFoundException::new);
		if (target.getRole() == AccountRole.ADMIN) {
			throw new InvalidAdminTargetException("관리자 계정은 대상으로 지정할 수 없습니다.");
		}
		userWarningIssuer.issue(targetId, null, UserWarningReason.ADMIN_MANUAL);
	}
}
