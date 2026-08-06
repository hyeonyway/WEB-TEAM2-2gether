package com.dbidding.account.authentication.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import jakarta.servlet.http.HttpSession;

public class SessionCsrfTokenService {

	public static final String CSRF_TOKEN_ATTRIBUTE = "CSRF_TOKEN";
	private static final int TOKEN_BYTES = 32;
	private final SecureRandom secureRandom = new SecureRandom();

	public String issue(HttpSession session) {
		byte[] randomBytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(randomBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		session.setAttribute(CSRF_TOKEN_ATTRIBUTE, token);
		return token;
	}

	public boolean matches(HttpSession session, String presentedToken) {
		Object storedToken = session.getAttribute(CSRF_TOKEN_ATTRIBUTE);
		if (!(storedToken instanceof String expectedToken) || presentedToken == null) {
			return false;
		}
		return MessageDigest.isEqual(
			expectedToken.getBytes(StandardCharsets.UTF_8),
			presentedToken.getBytes(StandardCharsets.UTF_8)
		);
	}
}
