package com.example.demo.common;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApiError(
		@Schema(example = "INVALID_REQUEST") String code,
		@Schema(example = "요청 값이 올바르지 않습니다.") String message
) {
}
