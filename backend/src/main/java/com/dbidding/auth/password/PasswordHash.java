package com.dbidding.auth.password;

public record PasswordHash(
	String encryptedPassword,
	String salt
) {
}
