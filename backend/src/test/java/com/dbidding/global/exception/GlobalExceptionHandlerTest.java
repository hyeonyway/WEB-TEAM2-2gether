package com.dbidding.global.exception;

import static org.assertj.core.api.Assertions.assertThat;
import org.springframework.web.bind.MissingServletRequestParameterException;
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

	@Test
	void 일반_API의_필수_파라미터_누락도_구조화된_400_응답으로_변환한다() {
		var response = handler.handleMissingRequestParameter(
			new MissingServletRequestParameterException("page", "int")
		);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isEqualTo(new ApiErrorResponse(
			"INVALID_REQUEST", "요청 정보를 확인해 주세요."
		));
	}

	private static class TestApiException extends ApiException {

		private TestApiException() {
			super(HttpStatus.CONFLICT, "TEST_CONFLICT", "테스트 충돌입니다.");
		}
	}
}
