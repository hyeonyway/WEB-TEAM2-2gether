package com.dbidding.auth.exception;

public class InvalidTokenRoleException extends InvalidTokenException {

    public InvalidTokenRoleException() {
        super("토큰 역할이 올바르지 않습니다.");
    }
}
