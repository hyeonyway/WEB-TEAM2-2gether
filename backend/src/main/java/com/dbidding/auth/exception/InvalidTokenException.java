package com.dbidding.auth.exception;

public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException() {
        super("유효하지 않은 토큰입니다.");
    }

    public InvalidTokenException(Throwable cause) {
        super("유효하지 않은 토큰입니다.", cause);
    }

    protected InvalidTokenException(String message) {
        super(message);
    }

    protected InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
