package com.dbidding.account.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InvalidAdminTargetException extends ApiException {

	public InvalidAdminTargetException(String message) {
		super(HttpStatus.BAD_REQUEST, "INVALID_ADMIN_TARGET", message);
	}
}
