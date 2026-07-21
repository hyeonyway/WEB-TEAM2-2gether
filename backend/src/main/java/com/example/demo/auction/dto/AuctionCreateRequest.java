package com.example.demo.auction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AuctionCreateRequest(
		@Schema(description = "경매 상품명", example = "무선 키보드")
		@NotBlank(message = "상품명은 필수입니다.")
		String title,

		@Schema(description = "시작 가격", example = "10000")
		@NotNull(message = "시작 가격은 필수입니다.")
		@Positive(message = "시작 가격은 0보다 커야 합니다.")
		BigDecimal startingPrice
) {
}
