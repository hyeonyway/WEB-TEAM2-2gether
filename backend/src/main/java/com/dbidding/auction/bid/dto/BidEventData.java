package com.dbidding.auction.bid.dto;

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
    /**
     * Redis 입찰 경로가 다음 리팩터링 전까지 이전 생성자 형태를 사용할 수 있도록 둔
     * 호환 생성자다. 시간 기준 ENDING 전환에서는 입찰이 마감 시간을 연장하지 않으므로
     * {@code closeTimeExtended} 값은 더 이상 이벤트 데이터에 보존하지 않는다.
     */
    public BidEventData(
            Integer itemId,
            Integer previousBidderId,
            Long previousBidId,
            Long startPrice,
            Long bidIncrement,
            AuctionStatus status,
            boolean closeTimeExtended,
            AuctionCloseData closeData
    ) {
        this(itemId, previousBidderId, previousBidId, startPrice, bidIncrement, status, closeData);
    }
}
