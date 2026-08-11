package com.dbidding.auction.stream;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.regex.Pattern;

public record BidAcceptedStreamEvent(
        String streamId,
        BidStreamEventType eventType,
        Integer auctionId,
        Long auctionVersion,
        Integer bidderId,
        Long requestedPrice,
        Long bidPrice,
        Integer previousBidderId,
        String idempotencyKey,
        String idempotencyRequestHash,
        Long currentPrice,
        Integer bidCount,
        Instant closeTime,
        AuctionStatus auctionStatus,
        Instant occurredAt
) implements AuctionWalletTimelineEvent {
    private static final Pattern STREAM_ID_PATTERN = Pattern.compile("\\d+-\\d+");
    private static final Pattern REQUEST_HASH_PATTERN = Pattern.compile("[0-9a-f]{64}");

    public static BidAcceptedStreamEvent from(String streamId, Map<String, String> values) {
        requireEventType(values);
        try {
            BidStreamEventType eventType = BidStreamEventType.from(required(values, "eventType"));
            AuctionStatus auctionStatus = AuctionStatus.valueOf(required(values, "auctionStatus"));
            if (eventType == BidStreamEventType.BUY_NOW && auctionStatus != AuctionStatus.ENDED) {
                throw new InvalidBidStreamEventException("즉시 낙찰 이벤트의 경매 상태는 ENDED여야 합니다.");
            }
            BidAcceptedStreamEvent event = new BidAcceptedStreamEvent(
                    streamId,
                    eventType,
                    Integer.valueOf(required(values, "auctionId")),
                    Long.valueOf(required(values, "auctionVersion")),
                    Integer.valueOf(required(values, "bidderId")),
                    Long.valueOf(required(values, "requestedPrice")),
                    Long.valueOf(required(values, "bidPrice")),
                    nullableInteger(values.get("previousBidderId")),
                    required(values, "idempotencyKey"),
                    required(values, "idempotencyRequestHash"),
                    Long.valueOf(required(values, "currentPrice")),
                    Integer.valueOf(required(values, "bidCount")),
                    Instant.parse(required(values, "closeTime")),
                    auctionStatus,
                    Instant.parse(required(values, "occurredAt"))
            );
            event.validateContract();
            return event;
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException("입찰 Stream 이벤트 형식이 올바르지 않습니다.", exception);
        }
    }

    private static void requireEventType(Map<String, String> values) {
        if (!"1".equals(values.get("schemaVersion"))) {
            throw new InvalidBidStreamEventException("지원하지 않는 입찰 Stream 이벤트입니다.");
        }
    }

    public boolean isBuyNow() {
        return eventType == BidStreamEventType.BUY_NOW;
    }

    private void validateContract() {
        if (streamId == null || !STREAM_ID_PATTERN.matcher(streamId).matches()) {
            throw new InvalidBidStreamEventException("Stream ID 형식이 올바르지 않습니다.");
        }
        if (auctionId == null || auctionId <= 0 || auctionVersion == null || auctionVersion <= 0
                || bidderId == null || bidderId <= 0 || requestedPrice == null || requestedPrice <= 0 || bidPrice == null || bidPrice <= 0
                || currentPrice == null || currentPrice <= 0 || bidCount == null || bidCount <= 0) {
            throw new InvalidBidStreamEventException("입찰 Stream 이벤트의 숫자 필드는 양수여야 합니다.");
        }
        if (previousBidderId != null && previousBidderId <= 0) {
            throw new InvalidBidStreamEventException("이전 최고 입찰자 ID는 양수여야 합니다.");
        }
        if (!bidPrice.equals(currentPrice)) {
            throw new InvalidBidStreamEventException("입찰가와 현재가는 일치해야 합니다.");
        }
        if (idempotencyKey.length() > 64) {
            throw new InvalidBidStreamEventException("Idempotency-Key는 64자 이하여야 합니다.");
        }
        if (!REQUEST_HASH_PATTERN.matcher(idempotencyRequestHash).matches()) {
            throw new InvalidBidStreamEventException("idempotencyRequestHash는 64자리 소문자 SHA-256 해시여야 합니다.");
        }
        if (!idempotencyRequestHash.equals(requestHash(requestedPrice))) {
            throw new InvalidBidStreamEventException("idempotencyRequestHash가 원 요청가와 일치하지 않습니다.");
        }
        if (isBuyNow()) {
            if (auctionStatus != AuctionStatus.ENDED) {
                throw new InvalidBidStreamEventException("즉시 낙찰 이벤트의 경매 상태는 ENDED여야 합니다.");
            }
            if (!occurredAt.equals(closeTime)) {
                throw new InvalidBidStreamEventException("즉시 낙찰 이벤트의 종료 시각은 승인 시각과 일치해야 합니다.");
            }
            return;
        }
        if (!occurredAt.isBefore(closeTime)) {
            throw new InvalidBidStreamEventException("입찰 발생 시각은 경매 마감 시각보다 이전이어야 합니다.");
        }
        if (!requestedPrice.equals(bidPrice)) {
            throw new InvalidBidStreamEventException("일반 입찰의 원 요청가와 승인 입찰가는 일치해야 합니다.");
        }
        if (auctionStatus != AuctionStatus.OPEN && auctionStatus != AuctionStatus.ENDING) {
            throw new InvalidBidStreamEventException("일반 입찰 이벤트의 경매 상태는 OPEN 또는 ENDING이어야 합니다.");
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

    private static String requestHash(long requestedPrice) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(String.valueOf(requestedPrice).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
