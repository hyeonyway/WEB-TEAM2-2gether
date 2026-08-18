package com.dbidding.auction.bid.dto;

import com.dbidding.auction.dto.BidResponses;

/**
 * {@code eventData}는 멱등 재생 응답인 경우 {@code null}이다 — 이 경우 호출자는 아무 이벤트도
 * 발행하지 않는다.
 */
public record BidExecutionResult(
        BidResponses.BidResult result,
        BidEventData eventData
) {
}
