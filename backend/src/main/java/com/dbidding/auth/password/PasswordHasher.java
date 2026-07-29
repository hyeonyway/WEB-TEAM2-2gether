package com.dbidding.auth.password;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import org.springframework.stereotype.Component;

@Component
public class PasswordHasher {

	private static final int SALT_BYTES = 16;
	private static final int KEY_BITS = 256;
	private static final int ITERATIONS = 600_000;
	private static final String ALGORITHM = "PBKDF2WithHmacSHA256";

	private final SecureRandom secureRandom = new SecureRandom();

	public PasswordHash hash(String rawPassword) {
		byte[] salt = new byte[SALT_BYTES];
		secureRandom.nextBytes(salt);
		byte[] encryptedPassword = derive(rawPassword, salt);
		return new PasswordHash(
			HexFormat.of().formatHex(encryptedPassword),
			HexFormat.of().formatHex(salt)
		);
	}

	public boolean matches(String rawPassword, String salt, String expectedHash) {
		byte[] actualHash = derive(rawPassword, HexFormat.of().parseHex(salt));
		return MessageDigest.isEqual(actualHash, HexFormat.of().parseHex(expectedHash));
	}

	private byte[] derive(String rawPassword, byte[] salt) {
		PBEKeySpec keySpec = new PBEKeySpec(rawPassword.toCharArray(), salt, ITERATIONS, KEY_BITS);
		try {
			SecretKeyFactory secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM);
			return secretKeyFactory.generateSecret(keySpec).getEncoded();
		} catch (GeneralSecurityException exception) {
			throw new IllegalStateException("비밀번호 해싱 알고리즘을 사용할 수 없습니다.", exception);
		} finally {
			keySpec.clearPassword();
		}
	}
}
