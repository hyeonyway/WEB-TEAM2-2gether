package com.dbidding.auth.dto;

public record RefreshResponse(
	String accessToken
) {

	@Override
	public String toString() {
		return "RefreshResponse[accessToken=<redacted>]";
	}
}
