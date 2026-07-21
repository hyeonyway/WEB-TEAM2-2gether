package com.example.demo.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record BidCreateRequest(
		@Schema(description = "입찰자 이름", example = "홍길동")
		@NotBlank(message = "입찰자 이름은 필수입니다.")
		String bidderName,

		@Schema(description = "입찰 금액", example = "15000")
		@NotNull(message = "입찰 금액은 필수입니다.")
		@Positive(message = "입찰 금액은 0보다 커야 합니다.")
		BigDecimal amount
) {
}
