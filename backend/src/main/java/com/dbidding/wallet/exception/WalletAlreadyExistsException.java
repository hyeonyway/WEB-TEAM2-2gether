package com.dbidding.wallet.exception;

public class WalletAlreadyExistsException extends RuntimeException {

	public WalletAlreadyExistsException() {
		super("이미 지갑이 존재합니다.");
	}
}
