package com.dbidding.account.exception;

public class ExpiredTokenException extends InvalidTokenException {

    public ExpiredTokenException(Throwable cause) {
        super("만료된 토큰입니다.", cause);
    }
}
