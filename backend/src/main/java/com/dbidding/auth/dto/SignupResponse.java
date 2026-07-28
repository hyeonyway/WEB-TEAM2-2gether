package com.dbidding.auth.dto;

public record SignupResponse(
	Integer id,
	String email,
	String nickname,
	String role,
	String status
) {
}
