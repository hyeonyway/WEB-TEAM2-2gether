package com.dbidding.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.global.security.JwtAuthFilter;
import com.dbidding.global.security.SseTicketAuthFilter;
import com.dbidding.global.security.TicketProvider;

@Configuration(proxyBeanMethods = false)
public class AuthFilterConfig {

	@Bean
	JwtAuthFilter jwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
		return new JwtAuthFilter(jwtTokenProvider);
	}

	@Bean
	SseTicketAuthFilter sseTicketAuthFilter(TicketProvider ticketProvider) {
		return new SseTicketAuthFilter(ticketProvider);
	}
}
