package com.dbidding.account.authentication.jwt;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dbidding.account.repository.AccountRepository;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.FilterErrorResponseWriter;
import com.dbidding.global.security.jwt.InMemoryTicketProvider;
import com.dbidding.global.security.jwt.JwtAuthFilter;
import com.dbidding.global.security.jwt.SseTicketAuthFilter;
import com.dbidding.global.security.jwt.TicketProvider;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.auth.mode", havingValue = "jwt", matchIfMissing = true)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtAuthenticationConfiguration {

	@Bean
	JwtTokenProvider jwtTokenProvider(JwtProperties properties) {
		return new JwtTokenProvider(properties);
	}

	@Bean
	RefreshTokenHasher refreshTokenHasher() {
		return new RefreshTokenHasher();
	}

	@Bean
	RefreshCookieFactory refreshCookieFactory(JwtProperties properties) {
		return new RefreshCookieFactory(properties);
	}

	@Bean
	JwtAuthenticationStrategy jwtAuthenticationStrategy(
		AuthenticationRepository authenticationRepository,
		JwtTokenProvider jwtTokenProvider,
		RefreshTokenHasher refreshTokenHasher,
		RefreshCookieFactory refreshCookieFactory,
		Clock clock
	) {
		return new JwtAuthenticationStrategy(
			authenticationRepository,
			jwtTokenProvider,
			refreshTokenHasher,
			refreshCookieFactory,
			clock
		);
	}

	@Bean
	JwtRefreshService jwtRefreshService(
		AccountRepository accountRepository,
		AuthenticationRepository authenticationRepository,
		JwtTokenProvider jwtTokenProvider,
		RefreshTokenHasher refreshTokenHasher,
		Clock clock
	) {
		return new JwtRefreshService(
			accountRepository,
			authenticationRepository,
			jwtTokenProvider,
			refreshTokenHasher,
			clock
		);
	}

	@Bean
	TicketProvider ticketProvider(Clock clock) {
		return new InMemoryTicketProvider(clock);
	}

	@Bean
	JwtAuthFilter jwtAuthFilter(
		JwtTokenProvider jwtTokenProvider,
		RequestUserIdWriter requestUserIdWriter,
		FilterErrorResponseWriter errorResponseWriter
	) {
		return new JwtAuthFilter(jwtTokenProvider, requestUserIdWriter, errorResponseWriter);
	}

	@Bean
	SseTicketAuthFilter sseTicketAuthFilter(
		TicketProvider ticketProvider,
		RequestUserIdWriter requestUserIdWriter,
		FilterErrorResponseWriter errorResponseWriter
	) {
		return new SseTicketAuthFilter(ticketProvider, requestUserIdWriter, errorResponseWriter);
	}
}
