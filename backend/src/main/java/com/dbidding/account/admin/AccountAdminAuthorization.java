package com.dbidding.account.admin;

import com.dbidding.account.domain.AccountRole;
import com.dbidding.account.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AccountAdminAuthorization {

	private final AccountRepository accountRepository;

	public void requireAdmin(Integer actorId) {
		boolean admin = accountRepository.findById(actorId)
			.map(account -> account.getRole() == AccountRole.ADMIN)
			.orElse(false);
		if (!admin) {
			throw new AccountAdminAccessDeniedException();
		}
	}
}
