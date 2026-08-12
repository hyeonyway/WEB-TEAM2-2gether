package com.dbidding.global.exception;

import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";
	private static final String INVALID_REQUEST_MESSAGE = "요청 정보를 확인해 주세요.";

	/**
	 * SSE 스트림 클라이언트가 먼저 연결을 끊은 뒤(예: 목록 스크롤로 구독 auctionIds가
	 * 바뀌어 EventSource를 재연결) 서버가 마무리 응답을 flush하려다 나는 예외다. 이미
	 * 끊긴 소켓이라 어차피 응답을 못 전달하므로 ResponseEntity를 만들지 않는다 — 억지로
	 * 500을 만들면 실제로는 아무도 못 받을 응답인데도 Prometheus/Grafana 5xx 지표만
	 * 오염시킨다. 로그도 ERROR로 남기면 스크롤할 때마다 Slack 알림이 쌓이므로 DEBUG로만
	 * 남긴다.
	 */
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	public void handleAsyncRequestNotUsable(AsyncRequestNotUsableException exception, HttpServletRequest request) {
		log.debug("event=async_request_already_closed requestUri={}", request.getRequestURI(), exception);
	}

	@ExceptionHandler(ApiException.class)
	public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
		return ResponseEntity.status(exception.getStatus()).body(new ApiErrorResponse(
			exception.getCode(),
			exception.getMessage()
		));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
		return invalidRequest(firstMessage(exception.getBindingResult().getAllErrors()));
	}

	@ExceptionHandler(BindException.class)
	public ResponseEntity<ApiErrorResponse> handleBindException(BindException exception) {
		return invalidRequest(firstMessage(exception.getAllErrors()));
	}

	/** SSE 전용 advice가 아닌 일반 API의 메서드 파라미터 검증 오류를 JSON 400으로 변환한다. */
	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(HandlerMethodValidationException exception) {
		return invalidRequest(firstMessage(exception.getAllErrors()));
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(MissingServletRequestParameterException exception) {
		return invalidRequest(INVALID_REQUEST_MESSAGE);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException exception) {
		return invalidRequest(INVALID_REQUEST_MESSAGE);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException exception) {
		return invalidRequest(INVALID_REQUEST_MESSAGE);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException exception) {
		return invalidRequest(INVALID_REQUEST_MESSAGE);
	}

	private ResponseEntity<ApiErrorResponse> invalidRequest(String message) {
		return ResponseEntity.badRequest().body(new ApiErrorResponse(INVALID_REQUEST, message));
	}

	private String firstMessage(Iterable<? extends MessageSourceResolvable> errors) {
		for (MessageSourceResolvable error : errors) {
			if (error.getDefaultMessage() != null && !error.getDefaultMessage().isBlank()) {
				return error.getDefaultMessage();
			}
		}
		return INVALID_REQUEST_MESSAGE;
	}

}
