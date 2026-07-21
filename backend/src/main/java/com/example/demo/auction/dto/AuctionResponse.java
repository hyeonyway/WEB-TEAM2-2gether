package com.example.demo.auction.dto;

import com.example.demo.auction.Auction;
import com.example.demo.auction.AuctionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record AuctionResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "무선 키보드") String title,
		@Schema(example = "10000") BigDecimal startingPrice,
		@Schema(example = "15000") BigDecimal currentPrice,
		@Schema(example = "OPEN") AuctionStatus status,
		@Schema(example = "홍길동", nullable = true) String winnerName
) {
	public static AuctionResponse from(Auction auction) {
		return new AuctionResponse(
				auction.getId(),
				auction.getTitle(),
				auction.getStartingPrice(),
				auction.getCurrentPrice(),
				auction.getStatus(),
				auction.getWinnerName()
		);
	}
}
