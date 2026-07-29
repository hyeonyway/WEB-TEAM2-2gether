package com.dbidding.auth.exception;

public class ExpiredTokenException extends InvalidTokenException {

    public ExpiredTokenException(Throwable cause) {
        super("만료된 토큰입니다.", cause);
    }
}
