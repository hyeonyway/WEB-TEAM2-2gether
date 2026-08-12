package com.dbidding.auction.stream;

import java.time.Instant;
import java.util.Map;

public record AuctionCloseRequestedStreamEvent(String streamId, Integer auctionId, Instant occurredAt) implements AuctionWalletTimelineEvent {
    static AuctionCloseRequestedStreamEvent from(String streamId, Map<String, String> values) {
        if (!"1".equals(values.get("schemaVersion"))) throw new InvalidBidStreamEventException("지원하지 않는 경매 종료 Stream 이벤트입니다.");
        try { return new AuctionCloseRequestedStreamEvent(streamId, Integer.valueOf(required(values, "auctionId")), Instant.parse(required(values, "occurredAt"))); }
        catch (IllegalArgumentException exception) { throw new InvalidBidStreamEventException("경매 종료 Stream 이벤트 형식이 올바르지 않습니다.", exception); }
    }
    public String archiveEventType() { return "auction.close-requested.v1"; }
    public int schemaVersion() { return 1; }
    public String archivePayload() { return "schemaVersion=1&auctionId=" + auctionId + "&occurredAt=" + occurredAt; }
    private static String required(Map<String, String> values, String key) { String value = values.get(key); if (value == null || value.isBlank()) throw new InvalidBidStreamEventException("필수 Stream field가 없습니다: " + key); return value; }
}
