package com.dbidding.account.authentication.jwt;

import com.dbidding.account.dto.RefreshResponse;

public record JwtRefreshResult(
	RefreshResponse response,
	String refreshToken
) {

	@Override
	public String toString() {
		return "JwtRefreshResult[response=" + response + ", refreshToken=<redacted>]";
	}
}
