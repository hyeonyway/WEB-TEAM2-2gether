package com.dbidding.auction.bid;

import com.dbidding.auction.domain.AuctionStatus;

/**
 * {@code BidExecutor}가 판단/wallet 처리만 하고 이벤트 조립·발행은 하지 않도록, 이벤트 조립에
 * 필요한 원시 필드만 담아 반환하는 내부 전용 타입. {@code auctionId}/{@code bidderId}/
 * {@code currentPrice}/{@code bidCount}/{@code closeTime}/{@code occurredAt}은 호출자
 * ({@code AuctionCommandService.participate()})가 이미 파라미터나 {@code BidResponses.BidResult}로
 * 갖고 있어 중복 필드로 두지 않는다.
 */
public record BidEventData(
        Integer itemId,
        Integer previousBidderId,
        Long previousBidId,
        Long startPrice,
        Long bidIncrement,
        AuctionStatus status,
        AuctionCloseData closeData
) {
}
