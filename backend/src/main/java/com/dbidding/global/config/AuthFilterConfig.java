package com.dbidding.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.dbidding.auth.token.JwtTokenProvider;
import com.dbidding.global.security.JwtAuthFilter;

@Configuration(proxyBeanMethods = false)
public class AuthFilterConfig {

	@Bean
	JwtAuthFilter jwtAuthFilter(JwtTokenProvider jwtTokenProvider) {
		return new JwtAuthFilter(jwtTokenProvider);
	}
}
