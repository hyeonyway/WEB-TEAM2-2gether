package com.dbidding.account.authentication.session;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.session")
public record SessionProperties(
	SessionStore store,
	String cookieName,
	boolean secureCookie
) {

	private static final String DEFAULT_COOKIE_NAME = "SESSION";

	public SessionProperties {
		store = store == null ? SessionStore.MEMORY : store;
		cookieName = cookieName == null || cookieName.isBlank() ? DEFAULT_COOKIE_NAME : cookieName;
	}
}
