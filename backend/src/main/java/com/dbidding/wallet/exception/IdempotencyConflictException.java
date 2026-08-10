package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class IdempotencyConflictException extends ApiException {

	public IdempotencyConflictException() {
		super(
			HttpStatus.CONFLICT,
			"IDEMPOTENCY_CONFLICT",
			"같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다."
		);
	}
}
