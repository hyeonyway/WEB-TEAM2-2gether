package com.dbidding.auth.dto;

import com.dbidding.account.domain.Account;

public record SignupResponse(
	Integer id,
	String email,
	String nickname,
	String role,
	String status
) {

	public static SignupResponse from(Account account) {
		return new SignupResponse(
			account.getId(),
			account.getEmail(),
			account.getNickname(),
			account.getRole().name(),
			account.getStatus().name()
		);
	}
}
