package com.dbidding.auth.dto;

import com.dbidding.auth.port.UserAccount;

public record SignupResponse(
	Integer id,
	String email,
	String nickname,
	String role,
	String status
) {

	public static SignupResponse from(UserAccount user) {
		return new SignupResponse(
			user.id(),
			user.email(),
			user.nickname(),
			user.role().name(),
			user.status()
		);
	}
}
