package com.dbidding.auth.cookie;

import java.time.Duration;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import com.dbidding.auth.config.JwtProperties;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefreshCookieFactory {

	private static final String COOKIE_NAME = "refreshToken";
	private static final String COOKIE_PATH = "/api/auth";
	private static final String SAME_SITE = "Strict";

	private final JwtProperties properties;

	public ResponseCookie create(String refreshToken) {
		return ResponseCookie.from(COOKIE_NAME, refreshToken)
			.httpOnly(true)
			.secure(properties.secureCookie())
			.sameSite(SAME_SITE)
			.path(COOKIE_PATH)
			.maxAge(Duration.ofSeconds(properties.refreshTokenSeconds()))
			.build();
	}

	public ResponseCookie expire() {
		return ResponseCookie.from(COOKIE_NAME, "")
			.httpOnly(true)
			.secure(properties.secureCookie())
			.sameSite(SAME_SITE)
			.path(COOKIE_PATH)
			.maxAge(Duration.ZERO)
			.build();
	}
}
