package com.dbidding.auction.stream;

import java.util.Map;
import java.time.Instant;

/** Redis Stream의 전역 순서를 공유하는 경매·지갑 상태 변경 이벤트. */
public sealed interface AuctionWalletTimelineEvent permits BidAcceptedStreamEvent, WalletStateChangedStreamEvent, AuctionCreatedStreamEvent, AuctionCloseRequestedStreamEvent {
    String streamId();

    String archiveEventType();

    int schemaVersion();

    Instant occurredAt();

    /** DB archive에 저장할 결정적 field 직렬화 값이다. */
    String archivePayload();

    static AuctionWalletTimelineEvent from(String streamId, Map<String, String> values) {
        String eventType = values.get("eventType");
        if (eventType != null && eventType.startsWith("wallet.")) {
            return WalletStateChangedStreamEvent.from(streamId, values);
        }
        if ("auction.created.v1".equals(eventType)) return AuctionCreatedStreamEvent.from(streamId, values);
        if ("auction.close-requested.v1".equals(eventType)) return AuctionCloseRequestedStreamEvent.from(streamId, values);
        return BidAcceptedStreamEvent.from(streamId, values);
    }
}
