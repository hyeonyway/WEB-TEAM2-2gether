package com.dbidding.auth;

public record PasswordHash(
	String encryptedPassword,
	String salt
) {
}
