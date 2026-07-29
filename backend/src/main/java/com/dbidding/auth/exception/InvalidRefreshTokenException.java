package com.dbidding.auth.exception;

public class InvalidRefreshTokenException extends InvalidTokenException {

	public InvalidRefreshTokenException() {
		super("유효하지 않은 Refresh Token입니다.");
	}
}
