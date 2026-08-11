package com.dbidding.auction.stream;

import com.dbidding.wallet.domain.HoldStatus;
import com.dbidding.wallet.domain.PointTransactionType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Redis가 승인한 단일 지갑 상태 전이의 사후 상태(snapshot)다.
 *
 * <p>consumer는 amount를 다시 계산하거나 WalletService를 호출하지 않고 이 snapshot을
 * walletVersion 조건으로 projection한다. 따라서 재전달은 DB 원장을 중복 생성하지 않는다.
 */
public record WalletStateChangedStreamEvent(
        String streamId,
        UUID eventId,
        String eventType,
        Integer userId,
        Long walletVersion,
        Long availableBalance,
        Long frozenBalance,
        Integer auctionId,
        Long holdAmount,
        HoldStatus holdStatus,
        PointTransactionType transactionType,
        Long transactionAmount,
        String idempotencyKey,
        Instant occurredAt
) implements AuctionWalletTimelineEvent {

    public static WalletStateChangedStreamEvent from(String streamId, Map<String, String> values) {
        if (!"2".equals(values.get("schemaVersion"))) {
            throw new InvalidBidStreamEventException("지원하지 않는 지갑 상태 Stream 이벤트입니다.");
        }
        try {
            WalletStateChangedStreamEvent event = new WalletStateChangedStreamEvent(
                    streamId,
                    UUID.fromString(required(values, "eventId")),
                    required(values, "eventType"),
                    Integer.valueOf(required(values, "userId")),
                    Long.valueOf(required(values, "walletVersion")),
                    Long.valueOf(required(values, "availableBalance")),
                    Long.valueOf(required(values, "frozenBalance")),
                    nullableInteger(values.get("auctionId")),
                    nullableLong(values.get("holdAmount")),
                    nullableHoldStatus(values.get("holdStatus")),
                    nullableTransactionType(values.get("transactionType")),
                    nullableLong(values.get("transactionAmount")),
                    values.get("idempotencyKey"),
                    Instant.parse(required(values, "occurredAt"))
            );
            event.validate();
            return event;
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException("지갑 상태 Stream 이벤트 형식이 올바르지 않습니다.", exception);
        }
    }

    @Override
    public String archiveEventType() {
        return eventType;
    }

    @Override
    public int schemaVersion() {
        return 2;
    }

    @Override
    public String archivePayload() {
        return "schemaVersion=2&eventId=" + eventId + "&eventType=" + eventType
                + "&userId=" + userId + "&walletVersion=" + walletVersion
                + "&availableBalance=" + availableBalance + "&frozenBalance=" + frozenBalance
                + "&auctionId=" + nullable(auctionId) + "&holdAmount=" + nullable(holdAmount)
                + "&holdStatus=" + nullable(holdStatus) + "&transactionType=" + nullable(transactionType)
                + "&transactionAmount=" + nullable(transactionAmount)
                + "&idempotencyKey=" + nullable(idempotencyKey) + "&occurredAt=" + occurredAt;
    }

    private void validate() {
        if (!eventType.matches("wallet\\.[a-z-]+\\.v1") || userId == null || userId <= 0
                || walletVersion == null || walletVersion <= 0 || availableBalance == null || availableBalance < 0
                || frozenBalance == null || frozenBalance < 0) {
            throw new InvalidBidStreamEventException("지갑 상태 Stream 이벤트의 값이 올바르지 않습니다.");
        }
        if ((holdAmount == null) != (holdStatus == null) || (holdAmount != null && (auctionId == null || auctionId <= 0 || holdAmount < 0))) {
            throw new InvalidBidStreamEventException("hold projection field가 올바르지 않습니다.");
        }
        if ((transactionType == null) != (transactionAmount == null) || (transactionAmount != null && transactionAmount <= 0)) {
            throw new InvalidBidStreamEventException("지갑 원장 field가 올바르지 않습니다.");
        }
        if (idempotencyKey != null && idempotencyKey.length() > 64) {
            throw new InvalidBidStreamEventException("Idempotency-Key는 64자 이하여야 합니다.");
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

    private static Long nullableLong(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : Long.valueOf(value);
    }

    private static HoldStatus nullableHoldStatus(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : HoldStatus.valueOf(value);
    }

    private static PointTransactionType nullableTransactionType(String value) {
        return value == null || value.isBlank() || "null".equals(value) ? null : PointTransactionType.valueOf(value);
    }

    private static String nullable(Object value) {
        return value == null ? "null" : value.toString();
    }
}
