package com.dbidding.account.password;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.password-hash")
public record PasswordHashProperties(@DefaultValue("600000") int iterations) {

	public PasswordHashProperties {
		if (iterations < 1) {
			throw new IllegalArgumentException("Password hash iterations must be positive");
		}
	}
}
