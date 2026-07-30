package com.dbidding.account.service;

import com.dbidding.account.dto.LoginResponse;

public record LoginResult(
	LoginResponse response,
	String refreshToken
) {

	@Override
	public String toString() {
		return "LoginResult[response=" + response + ", refreshToken=<redacted>]";
	}
}
