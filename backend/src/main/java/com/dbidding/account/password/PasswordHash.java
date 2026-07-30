package com.dbidding.account.password;

public record PasswordHash(
	String encryptedPassword,
	String salt
) {
}
