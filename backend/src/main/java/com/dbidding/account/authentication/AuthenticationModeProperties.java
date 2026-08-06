package com.dbidding.account.authentication;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.auth")
public record AuthenticationModeProperties(AuthenticationMode mode) {

	public AuthenticationModeProperties {
		mode = mode == null ? AuthenticationMode.JWT : mode;
	}
}
