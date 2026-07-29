package com.dbidding.wallet.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException() {
		super("같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
	}
}
