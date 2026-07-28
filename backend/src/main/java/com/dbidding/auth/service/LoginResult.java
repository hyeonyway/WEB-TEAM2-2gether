package com.dbidding.auth.service;

import com.dbidding.auth.dto.LoginResponse;

public record LoginResult(
	LoginResponse response,
	String refreshToken
) {

	@Override
	public String toString() {
		return "LoginResult[response=" + response + ", refreshToken=<redacted>]";
	}
}
