package com.dbidding.global.exception;

public record ApiErrorResponse(
	String code,
	String message
) {
}
