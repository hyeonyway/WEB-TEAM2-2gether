package com.dbidding.account.service;

import com.dbidding.account.dto.RefreshResponse;

public record RefreshResult(
	RefreshResponse response,
	String refreshToken
) {

	@Override
	public String toString() {
		return "RefreshResult[response=" + response + ", refreshToken=<redacted>]";
	}
}
