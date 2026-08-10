package com.dbidding.global.security.session;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;

import com.dbidding.account.authentication.session.SessionAuthenticationStrategy;
import com.dbidding.account.authentication.session.SessionCsrfTokenService;
import com.dbidding.account.authentication.session.SessionProperties;
import com.dbidding.global.security.RequestUserIdWriter;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "session")
@EnableConfigurationProperties(SessionProperties.class)
public class SessionAuthConfiguration {

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(
		SessionProperties properties,
		Clock clock,
		SessionCsrfTokenService csrfTokenService,
		SessionSseConnectionRegistry sessionSseConnectionRegistry
	) {
		return new SessionAuthenticationStrategy(properties, clock, csrfTokenService, sessionSseConnectionRegistry);
	}

	@Bean
	SessionAuthFilter sessionAuthFilter(RequestUserIdWriter requestUserIdWriter) {
		return new SessionAuthFilter(requestUserIdWriter);
	}

	@Bean
	SessionCsrfTokenService sessionCsrfTokenService() {
		return new SessionCsrfTokenService();
	}

	@Bean
	ServletListenerRegistrationBean<SessionSseCleanupListener> sessionSseCleanupListener(
		SessionSseConnectionRegistry registry
	) { return new ServletListenerRegistrationBean<>(new SessionSseCleanupListener(registry)); }

	@Bean
	SessionCsrfFilter sessionCsrfFilter(SessionCsrfTokenService tokenService) {
		return new SessionCsrfFilter(tokenService);
	}
}
