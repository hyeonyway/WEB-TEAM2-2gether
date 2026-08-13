package com.dbidding.auction.stream;

import java.time.Instant;
import java.util.Map;

public record AuctionEndingStartedStreamEvent(
        String streamId,
        Integer auctionId,
        Instant closeTime,
        Instant occurredAt
) implements AuctionWalletTimelineEvent {
    static AuctionEndingStartedStreamEvent from(String streamId, Map<String, String> values) {
        if (!"1".equals(values.get("schemaVersion"))) {
            throw new InvalidBidStreamEventException("지원하지 않는 ENDING Stream 이벤트입니다.");
        }
        try {
            return new AuctionEndingStartedStreamEvent(streamId, Integer.valueOf(required(values, "auctionId")),
                    Instant.parse(required(values, "closeTime")), Instant.parse(required(values, "occurredAt")));
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException("ENDING Stream 이벤트 형식이 올바르지 않습니다.", exception);
        }
    }

    @Override
    public String archiveEventType() { return "auction.ending-started.v1"; }

    @Override
    public int schemaVersion() { return 1; }

    @Override
    public String archivePayload() { return "schemaVersion=1&auctionId=" + auctionId + "&closeTime=" + closeTime + "&occurredAt=" + occurredAt; }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) throw new InvalidBidStreamEventException("필수 Stream field가 없습니다: " + key);
        return value;
    }
}
