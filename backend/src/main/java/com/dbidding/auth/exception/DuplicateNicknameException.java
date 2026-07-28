package com.dbidding.auth.exception;

public class DuplicateNicknameException extends RuntimeException {

	public DuplicateNicknameException() {
		super("이미 사용 중인 닉네임입니다.");
	}

	public DuplicateNicknameException(Throwable cause) {
		super("이미 사용 중인 닉네임입니다.", cause);
	}
}
