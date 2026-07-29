package com.dbidding.auth.exception;

public class InvalidTokenTypeException extends InvalidTokenException {

    public InvalidTokenTypeException() {
        super("토큰 유형이 올바르지 않습니다.");
    }
}
