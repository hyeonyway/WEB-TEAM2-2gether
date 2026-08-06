package com.dbidding.account.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthenticationModeProperties(AuthenticationMode mode, boolean sessionEnabled) {

	public AuthenticationModeProperties {
		mode = mode == null ? AuthenticationMode.JWT : mode;
		if (mode == AuthenticationMode.SESSION && !sessionEnabled) {
			throw new IllegalArgumentException("Session authentication requires app.auth.session-enabled=true");
		}
	}
}
