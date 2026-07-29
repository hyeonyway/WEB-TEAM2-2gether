package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidIdempotencyKeyException extends RuntimeException {

	public InvalidIdempotencyKeyException() {
		super("Idempotency-Key는 1자 이상 64자 이하여야 합니다.");
	}
}
