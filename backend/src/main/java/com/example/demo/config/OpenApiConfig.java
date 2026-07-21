package com.example.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI auctionOpenApi() {
		return new OpenAPI().info(new Info()
				.title("2gether Auction API")
				.description("Spring Boot 경매 CRUD 예제 API")
				.version("v1"));
	}
}
