package com.example.demo.auction.dto;

import com.example.demo.auction.Bid;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record BidResponse(
		@Schema(example = "1") Long id,
		@Schema(example = "1") Long auctionId,
		@Schema(example = "홍길동") String bidderName,
		@Schema(example = "15000") BigDecimal amount
) {
	public static BidResponse from(Bid bid, Long auctionId) {
		return new BidResponse(bid.getId(), auctionId, bid.getBidderName(), bid.getAmount());
	}
}
