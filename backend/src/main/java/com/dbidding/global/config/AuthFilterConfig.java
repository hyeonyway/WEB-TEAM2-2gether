package com.dbidding.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dbidding.account.token.JwtTokenProvider;
import com.dbidding.global.security.JwtAuthFilter;
import com.dbidding.global.security.RequestUserIdWriter;
import com.dbidding.global.security.SseTicketAuthFilter;
import com.dbidding.global.security.TicketProvider;

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
