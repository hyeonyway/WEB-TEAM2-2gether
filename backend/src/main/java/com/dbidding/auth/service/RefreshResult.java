package com.dbidding.auth.service;

import com.dbidding.auth.dto.RefreshResponse;

public record RefreshResult(
	RefreshResponse response,
	String refreshToken
) {

	@Override
	public String toString() {
		return "RefreshResult[response=" + response + ", refreshToken=<redacted>]";
	}
}
