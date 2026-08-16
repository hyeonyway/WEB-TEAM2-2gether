package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StreamEventValidationMatrixTest {
    @Test
    void 주문_상태_이벤트는_schema와_필수값이_없으면_거부한다() {
        assertThatThrownBy(() -> OrderStateChangedStreamEvent.from("1-0", Map.of(
                "schemaVersion", "2", "eventType", "order.completed.v1")))
                .isInstanceOf(InvalidBidStreamEventException.class);
        assertThatThrownBy(() -> OrderStateChangedStreamEvent.from("1-0", Map.of(
                "schemaVersion", "1", "eventType", "order.completed.v1", "eventId", UUID.randomUUID().toString())))
                .isInstanceOf(InvalidBidStreamEventException.class);
    }

    @Test
    void 지갑_상태_이벤트는_schema와_원장_조합이_잘못되면_거부한다() {
        assertThatThrownBy(() -> WalletStateChangedStreamEvent.from("1-0", Map.of(
                "schemaVersion", "1", "eventType", "wallet.charged.v1")))
                .isInstanceOf(InvalidBidStreamEventException.class);
        assertThatThrownBy(() -> WalletStateChangedStreamEvent.from("1-0", Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.charged.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "1"), Map.entry("availableBalance", "1"), Map.entry("frozenBalance", "0"),
                Map.entry("transactionType", "CHARGE"), Map.entry("transactionAmount", "0"),
                Map.entry("occurredAt", "2026-08-10T12:00:00Z"))))
                .isInstanceOf(InvalidBidStreamEventException.class);
    }
}
