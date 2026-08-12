package com.dbidding.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

	private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

	@Test
	void 공통_API_예외를_지정한_상태와_오류_응답으로_변환한다() {
		var response = handler.handleApiException(new TestApiException());

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
			"TEST_CONFLICT",
			"테스트 충돌입니다."
		));
	}

	private static class TestApiException extends ApiException {

		private TestApiException() {
			super(HttpStatus.CONFLICT, "TEST_CONFLICT", "테스트 충돌입니다.");
		}
	}
}
