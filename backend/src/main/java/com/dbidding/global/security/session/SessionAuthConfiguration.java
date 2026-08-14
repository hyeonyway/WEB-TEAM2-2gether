package com.dbidding.global.security.session;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;

import com.dbidding.account.authentication.session.SessionAuthenticationStrategy;
import com.dbidding.account.authentication.session.SessionCsrfTokenService;
import com.dbidding.account.authentication.session.SessionProperties;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.FilterErrorResponseWriter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SessionProperties.class)
public class SessionAuthConfiguration {

	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(
		SessionProperties properties,
		Clock clock,
		SessionCsrfTokenService csrfTokenService,
		SessionSseTerminationPublisher sessionSseTerminationPublisher,
		org.springframework.beans.factory.ObjectProvider<org.springframework.session.FindByIndexNameSessionRepository<?>> sessionRepositoryProvider
	) {
		return new SessionAuthenticationStrategy(properties, clock, csrfTokenService, sessionSseTerminationPublisher, sessionRepositoryProvider);
	}

	@Bean
	SessionAuthFilter sessionAuthFilter(
		RequestUserIdWriter requestUserIdWriter,
		FilterErrorResponseWriter errorResponseWriter, SessionProperties properties, Clock clock, SessionSseTerminationPublisher publisher
	) {
		return new SessionAuthFilter(requestUserIdWriter, errorResponseWriter, properties, clock, publisher);
	}

	@Bean
	SessionCsrfTokenService sessionCsrfTokenService() {
		return new SessionCsrfTokenService();
	}

	@Bean
	SessionSseCleanupListener sessionSseCleanupListener(
		SessionSseConnectionRegistry registry
	) { return new SessionSseCleanupListener(registry); }

	@Bean
	ServletListenerRegistrationBean<SessionSseCleanupListener> sessionSseCleanupListenerRegistration(
		SessionSseCleanupListener sessionSseCleanupListener
	) { return new ServletListenerRegistrationBean<>(sessionSseCleanupListener); }

	@Bean
	SessionCsrfFilter sessionCsrfFilter(
		SessionCsrfTokenService tokenService,
		FilterErrorResponseWriter errorResponseWriter
	) {
		return new SessionCsrfFilter(tokenService, errorResponseWriter);
	}
}
