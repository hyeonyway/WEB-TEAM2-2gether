package com.dbidding.account.authentication;

import com.dbidding.account.domain.AccountRole;

public record AuthenticatedAccount(
	Integer userId,
	AccountRole role
) {
}
