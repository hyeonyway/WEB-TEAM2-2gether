package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.Instant;
import java.util.Map;

public record BidAcceptedStreamEvent(
        String streamId,
        Integer auctionId,
        Long auctionVersion,
        Integer bidderId,
        Long bidPrice,
        Integer previousBidderId,
        String idempotencyKey,
        String idempotencyRequestHash,
        Long currentPrice,
        Integer bidCount,
        Instant closeTime,
        AuctionStatus auctionStatus,
        Instant occurredAt
) {
    public static BidAcceptedStreamEvent from(String streamId, Map<String, String> values) {
        requireEventType(values);
        try {
            return new BidAcceptedStreamEvent(
                    streamId,
                    Integer.valueOf(required(values, "auctionId")),
                    Long.valueOf(required(values, "auctionVersion")),
                    Integer.valueOf(required(values, "bidderId")),
                    Long.valueOf(required(values, "bidPrice")),
                    nullableInteger(values.get("previousBidderId")),
                    required(values, "idempotencyKey"),
                    required(values, "idempotencyRequestHash"),
                    Long.valueOf(required(values, "currentPrice")),
                    Integer.valueOf(required(values, "bidCount")),
                    Instant.parse(required(values, "closeTime")),
                    AuctionStatus.valueOf(required(values, "auctionStatus")),
                    Instant.parse(required(values, "occurredAt"))
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException("입찰 Stream 이벤트 형식이 올바르지 않습니다.", exception);
        }
    }

    private static void requireEventType(Map<String, String> values) {
        if (!"bid.accepted.v1".equals(values.get("eventType")) || !"1".equals(values.get("schemaVersion"))) {
            throw new InvalidBidStreamEventException("지원하지 않는 입찰 Stream 이벤트입니다.");
        }
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new InvalidBidStreamEventException("필수 Stream field가 없습니다: " + key);
        }
        return value;
    }

    private static Integer nullableInteger(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : Integer.valueOf(value);
    }
}
