package com.dbidding.account.authentication.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.session")
public final class SessionProperties {

	private static final String DEFAULT_COOKIE_NAME = "SESSION";

	private final SessionStore store;
	private final String cookieName;
	private final boolean secureCookie;

	public SessionProperties(SessionStore store, String cookieName, Boolean secureCookie) {
		if (store == null) {
			throw new IllegalArgumentException("Session store must be explicitly configured");
		}
		this.store = store;
		this.cookieName = cookieName == null || cookieName.isBlank() ? DEFAULT_COOKIE_NAME : cookieName;
		this.secureCookie = secureCookie == null || secureCookie;
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
}
