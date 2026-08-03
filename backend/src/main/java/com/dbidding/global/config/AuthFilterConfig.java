package com.dbidding.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dbidding.account.authentication.jwt.JwtTokenProvider;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.jwt.JwtAuthFilter;
import com.dbidding.global.security.jwt.SseTicketAuthFilter;
import com.dbidding.global.security.jwt.TicketProvider;

@Configuration(proxyBeanMethods = false)
public class AuthFilterConfig {

	@Bean
	JwtAuthFilter jwtAuthFilter(JwtTokenProvider jwtTokenProvider, RequestUserIdWriter requestUserIdWriter) {
		return new JwtAuthFilter(jwtTokenProvider, requestUserIdWriter);
	}

	@Bean
	SseTicketAuthFilter sseTicketAuthFilter(TicketProvider ticketProvider, RequestUserIdWriter requestUserIdWriter) {
		return new SseTicketAuthFilter(ticketProvider, requestUserIdWriter);
	}
}
