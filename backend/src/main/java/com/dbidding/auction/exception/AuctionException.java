package com.dbidding.auction.exception;

import org.springframework.http.HttpStatus;

import com.dbidding.global.exception.ApiException;

public class AuctionException extends ApiException {

	private AuctionException(HttpStatus status, String code, String message) {
		super(status, code, message);
	}

	public static AuctionException notFound() {
		return new AuctionException(HttpStatus.NOT_FOUND, "AUCTION_NOT_FOUND", "경매를 찾을 수 없습니다.");
	}

	public static AuctionException sellerBidForbidden() {
		return new AuctionException(
			HttpStatus.FORBIDDEN,
			"AUCTION_SELLER_BID_FORBIDDEN",
			"판매자는 자신의 경매에 입찰할 수 없습니다."
		);
	}

	public static AuctionException invalidRequest(String message) {
		return new AuctionException(HttpStatus.BAD_REQUEST, "INVALID_AUCTION_REQUEST", message);
	}

	public static AuctionException invalidBidRequest(String message) {
		return new AuctionException(HttpStatus.BAD_REQUEST, "INVALID_BID_REQUEST", message);
	}

	public static AuctionException invalidCursor() {
		return new AuctionException(HttpStatus.BAD_REQUEST, "INVALID_AUCTION_CURSOR", "유효하지 않은 경매 cursor입니다.");
	}

	public static AuctionException invalidIdempotencyKey(String message) {
		return new AuctionException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", message);
	}

	public static AuctionException idempotencyConflict() {
		return new AuctionException(
			HttpStatus.CONFLICT,
			"IDEMPOTENCY_CONFLICT",
			"같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다."
		);
	}

	public static AuctionException leadingBidderConflict() {
		return new AuctionException(
			HttpStatus.CONFLICT,
			"AUCTION_LEADING_BIDDER_CONFLICT",
			"현재 최고 입찰자는 추가 입찰할 수 없습니다."
		);
	}

	public static AuctionException cardNotFound() {
		return new AuctionException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "카드를 찾을 수 없습니다.");
	}

	public static AuctionException stateRecoveryRequired() {
		return new AuctionException(HttpStatus.SERVICE_UNAVAILABLE, "STATE_RECOVERY_REQUIRED", "실시간 상태 복구가 완료되지 않았습니다.");
	}
}
