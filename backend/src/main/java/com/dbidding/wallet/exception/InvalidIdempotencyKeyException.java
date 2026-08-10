package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class InvalidIdempotencyKeyException extends ApiException {

	public InvalidIdempotencyKeyException() {
		super(
			HttpStatus.BAD_REQUEST,
			"INVALID_IDEMPOTENCY_KEY",
			"Idempotency-Key는 1자 이상 64자 이하여야 합니다."
		);
	}
}
