package com.dbidding.auth.exception;

public class DuplicateEmailException extends RuntimeException {

	public DuplicateEmailException() {
		super("이미 사용 중인 이메일입니다.");
	}

	public DuplicateEmailException(Throwable cause) {
		super("이미 사용 중인 이메일입니다.", cause);
	}
}
