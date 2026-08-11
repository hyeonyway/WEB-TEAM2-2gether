package com.dbidding.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;

import tools.jackson.databind.ObjectMapper;

class FilterErrorResponseWriterTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final FilterErrorResponseWriter writer = new FilterErrorResponseWriter(objectMapper);

	@Test
	void JSON_콘텐츠_타입과_UTF_8_인코딩의_공통_오류_응답을_작성한다() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		writer.write(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "유효하지 않은 인증 토큰입니다.");

		assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
		assertThat(response.getContentType()).startsWith("application/json");
		assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
		var body = objectMapper.readTree(response.getContentAsString());
		assertThat(body.path("code").asText()).isEqualTo("INVALID_TOKEN");
		assertThat(body.path("message").asText()).isEqualTo("유효하지 않은 인증 토큰입니다.");
	}
}
