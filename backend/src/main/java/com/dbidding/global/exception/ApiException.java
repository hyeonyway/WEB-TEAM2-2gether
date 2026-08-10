package com.dbidding.global.exception;

import org.springframework.http.HttpStatusCode;

public abstract class ApiException extends RuntimeException {

	private final HttpStatusCode status;
	private final String code;

	protected ApiException(HttpStatusCode status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	protected ApiException(HttpStatusCode status, String code, String message, Throwable cause) {
		super(message, cause);
		this.status = status;
		this.code = code;
	}

	public HttpStatusCode getStatus() {
		return status;
	}

	public String getCode() {
		return code;
	}
}
