package com.dbidding.account.authentication.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.session")
public final class SessionProperties {

	private static final String DEFAULT_COOKIE_NAME = "SESSION";

	private final SessionStore store;
	private final String cookieName;
	private final boolean secureCookie;
	private final String sameSite;
	private final Duration absoluteTimeout;

	public SessionProperties(SessionStore store, String cookieName, Boolean secureCookie, String sameSite, Duration absoluteTimeout) {
		if (store == null) {
			throw new IllegalArgumentException("Session store must be explicitly configured");
		}
		this.store = store;
		this.cookieName = cookieName == null || cookieName.isBlank() ? DEFAULT_COOKIE_NAME : cookieName;
		this.secureCookie = secureCookie == null || secureCookie;
		this.sameSite = sameSite == null || sameSite.isBlank() ? "Lax" : normalizeSameSite(sameSite);
		this.absoluteTimeout = absoluteTimeout == null ? Duration.ofHours(12) : absoluteTimeout;
		if (this.absoluteTimeout.isZero() || this.absoluteTimeout.isNegative()) throw new IllegalArgumentException("Session absolute timeout must be positive");
		if ("None".equals(this.sameSite) && !this.secureCookie) {
			throw new IllegalArgumentException("SameSite=None requires a secure cookie");
		}
	}

	public Duration absoluteTimeout() { return absoluteTimeout; }

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
