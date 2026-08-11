package com.dbidding.auction.stream;

import java.util.Map;

/** Redis Stream의 전역 순서를 공유하는 경매·지갑 상태 변경 이벤트. */
public sealed interface AuctionWalletTimelineEvent permits BidAcceptedStreamEvent, WalletChargedStreamEvent {
    String streamId();

    static AuctionWalletTimelineEvent from(String streamId, Map<String, String> values) {
        return "wallet.charged.v1".equals(values.get("eventType"))
                ? WalletChargedStreamEvent.from(streamId, values)
                : BidAcceptedStreamEvent.from(streamId, values);
    }
}
