package com.dbidding.auth.port;

public record UserAccount(
	Integer id,
	String email,
	String nickname,
	UserAccountRole role,
	String status,
	String encryptedPassword,
	String salt
) {
}
