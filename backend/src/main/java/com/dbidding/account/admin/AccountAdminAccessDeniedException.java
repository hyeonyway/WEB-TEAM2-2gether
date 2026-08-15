package com.dbidding.account.admin;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class AccountAdminAccessDeniedException extends ApiException {

	public AccountAdminAccessDeniedException() {
		super(HttpStatus.FORBIDDEN, "ACCOUNT_ADMIN_ACCESS_DENIED", "관리자만 계정 상태를 변경할 수 있습니다.");
	}
}
