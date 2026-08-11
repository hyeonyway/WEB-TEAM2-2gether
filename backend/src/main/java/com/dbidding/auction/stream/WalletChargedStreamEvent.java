package com.dbidding.auction.stream;

import java.time.Instant;
import java.util.Map;

/** Redis에서 승인된 지갑 충전을 DB wallet.point와 point_records에 반영한다. */
public record WalletChargedStreamEvent(
        String streamId,
        Integer userId,
        Long amount,
        String idempotencyKey,
        Instant occurredAt
) implements AuctionWalletTimelineEvent {
    public static WalletChargedStreamEvent from(String streamId, Map<String, String> values) {
        if (!"1".equals(values.get("schemaVersion"))) {
            throw new InvalidBidStreamEventException("지원하지 않는 지갑 Stream 이벤트입니다.");
        }
        try {
            WalletChargedStreamEvent event = new WalletChargedStreamEvent(
                    streamId,
                    Integer.valueOf(required(values, "userId")),
                    Long.valueOf(required(values, "amount")),
                    required(values, "idempotencyKey"),
                    Instant.parse(required(values, "occurredAt"))
            );
            if (event.userId <= 0 || event.amount <= 0 || event.idempotencyKey.length() > 64) {
                throw new InvalidBidStreamEventException("지갑 충전 이벤트의 값이 올바르지 않습니다.");
            }
            return event;
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException("지갑 충전 Stream 이벤트 형식이 올바르지 않습니다.", exception);
        }
    }

    @Override
    public String archiveEventType() {
        return "wallet.charged.v1";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String archivePayload() {
        return "schemaVersion=1&userId=" + userId
                + "&amount=" + amount
                + "&idempotencyKey=" + idempotencyKey
                + "&occurredAt=" + occurredAt;
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new InvalidBidStreamEventException("필수 Stream field가 없습니다: " + key);
        }
        return value;
    }
}
