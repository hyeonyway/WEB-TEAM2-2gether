package com.dbidding.global.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

@TestConfiguration(proxyBeanMethods = false)
class FilterErrorResponseWriterTestConfiguration {

	@Bean
	ObjectMapper objectMapper() {
		return new ObjectMapper();
	}
}
