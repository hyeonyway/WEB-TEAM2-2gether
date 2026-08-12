package com.dbidding.auction.stream;

import java.time.Instant;
import java.util.Map;

public record AuctionCreatedStreamEvent(String streamId, Integer sellerId, Integer itemId, String auctionName,
        String description, Long startPrice, Long buyNowPrice, Long deliveryFee, Long bidPriceUnit,
        Instant closeTime, String idempotencyKey, String idempotencyRequestHash, Instant occurredAt) implements AuctionWalletTimelineEvent {
    static AuctionCreatedStreamEvent from(String streamId, Map<String, String> v) {
        try {
            if (!"1".equals(v.get("schemaVersion"))) throw new InvalidBidStreamEventException("지원하지 않는 경매 생성 Stream 이벤트입니다.");
            return new AuctionCreatedStreamEvent(streamId, Integer.valueOf(required(v,"sellerId")), Integer.valueOf(required(v,"itemId")),
                    required(v,"auctionName"), required(v,"description"), Long.valueOf(required(v,"startPrice")),
                    nullableLong(v.get("buyNowPrice")), Long.valueOf(required(v,"deliveryFee")), Long.valueOf(required(v,"bidPriceUnit")),
                    Instant.parse(required(v,"closeTime")), required(v,"idempotencyKey"), required(v,"idempotencyRequestHash"), Instant.parse(required(v,"occurredAt")));
        } catch (IllegalArgumentException e) { throw new InvalidBidStreamEventException("경매 생성 Stream 이벤트 형식이 올바르지 않습니다.", e); }
    }
    public String archiveEventType() { return "auction.created.v1"; }
    public int schemaVersion() { return 1; }
    public String archivePayload() { return "schemaVersion=1&sellerId="+sellerId+"&itemId="+itemId+"&auctionName="+auctionName+"&description="+description+"&startPrice="+startPrice+"&buyNowPrice="+buyNowPrice+"&deliveryFee="+deliveryFee+"&bidPriceUnit="+bidPriceUnit+"&closeTime="+closeTime+"&idempotencyKey="+idempotencyKey+"&idempotencyRequestHash="+idempotencyRequestHash+"&occurredAt="+occurredAt; }
    private static String required(Map<String,String> v,String k) { String x=v.get(k); if(x==null||x.isBlank()) throw new InvalidBidStreamEventException("필수 Stream field가 없습니다: "+k); return x; }
    private static Long nullableLong(String x) { return x==null||x.isBlank()||"null".equals(x)?null:Long.valueOf(x); }
}
