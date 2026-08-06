package com.dbidding.account.authentication.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.session")
public final class SessionProperties {

	private static final String DEFAULT_COOKIE_NAME = "SESSION";

	private final SessionStore store;
	private final String cookieName;
	private final boolean secureCookie;
	private final String sameSite;

	public SessionProperties(SessionStore store, String cookieName, Boolean secureCookie, String sameSite) {
		if (store == null) {
			throw new IllegalArgumentException("Session store must be explicitly configured");
		}
		this.store = store;
		this.cookieName = cookieName == null || cookieName.isBlank() ? DEFAULT_COOKIE_NAME : cookieName;
		this.secureCookie = secureCookie == null || secureCookie;
		this.sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : normalizeSameSite(sameSite);
	}

	public SessionStore store() {
		return store;
	}

	public String cookieName() {
		return cookieName;
	}

	public boolean secureCookie() {
		return secureCookie;
	}

	public String sameSite() {
		return sameSite;
	}

	private String normalizeSameSite(String value) {
		return switch (value.toLowerCase()) {
			case "lax" -> "Lax";
			case "strict" -> "Strict";
			case "none" -> "None";
			default -> throw new IllegalArgumentException("Unsupported SameSite policy: " + value);
		};
	}
}
