package com.dbidding.global.exception;

import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class GlobalExceptionHandler {

	private static final String INVALID_REQUEST = "INVALID_REQUEST";
	private static final String INVALID_REQUEST_MESSAGE = "요청 정보를 확인해 주세요.";

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

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleHandlerMethodValidation(
		HandlerMethodValidationException exception
	) {
		return invalidRequest(firstMessage(exception.getAllErrors()));
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestHeader(MissingRequestHeaderException exception) {
		return invalidRequest(INVALID_REQUEST_MESSAGE);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
		MissingServletRequestParameterException exception
	) {
		return invalidRequest(INVALID_REQUEST_MESSAGE);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodArgumentTypeMismatch(
		MethodArgumentTypeMismatchException exception
	) {
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
