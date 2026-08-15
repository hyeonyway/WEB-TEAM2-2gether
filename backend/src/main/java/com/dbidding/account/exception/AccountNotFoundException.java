package com.dbidding.account.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class AccountNotFoundException extends ApiException {

	public AccountNotFoundException() {
		super(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "계정을 찾을 수 없습니다.");
	}
}
