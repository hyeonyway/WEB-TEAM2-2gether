package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class BidAcceptedStreamEventTest {
    @Test
    void 승인된_입찰_stream_계약을_파싱한다() {
        BidAcceptedStreamEvent event = BidAcceptedStreamEvent.from("1720000000000-0", fields());

        assertThat(event.auctionId()).isEqualTo(10);
        assertThat(event.eventType()).isEqualTo(BidStreamEventType.BID_ACCEPTED);
        assertThat(event.auctionVersion()).isEqualTo(3L);
        assertThat(event.previousBidderId()).isNull();
        assertThat(event.bidPrice()).isEqualTo(12_000L);
    }

    @Test
    void 지원하지_않는_이벤트_타입은_거부한다() {
        Map<String, String> fields = fields();
        fields.put("eventType", "bid.rejected.v1");

        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class);
    }

    @Test
    void 즉시낙찰_이벤트를_파싱한다() {
        Map<String, String> fields = fields();
        fields.put("eventType", "auction.buy-now.v1");
        fields.put("auctionStatus", "ENDED");
        fields.put("closeTime", "2026-08-10T11:00:00Z");

        BidAcceptedStreamEvent event = BidAcceptedStreamEvent.from("1720000000000-1", fields);

        assertThat(event.isBuyNow()).isTrue();
    }

    @Test
    void 입찰가와_현재가가_다르면_거부한다() {
        Map<String, String> fields = fields();
        fields.put("currentPrice", "13000");

        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("입찰가와 현재가");
    }

    @Test
    void idempotency_hash가_SHA256_형식이_아니면_거부한다() {
        Map<String, String> fields = fields();
        fields.put("idempotencyRequestHash", "invalid");

        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("SHA-256");
    }

    private Map<String, String> fields() {
        return new java.util.HashMap<>(Map.ofEntries(
                Map.entry("eventType", "bid.accepted.v1"),
                Map.entry("schemaVersion", "1"),
                Map.entry("auctionId", "10"),
                Map.entry("auctionVersion", "3"),
                Map.entry("bidderId", "2"),
                Map.entry("requestedPrice", "12000"),
                Map.entry("bidPrice", "12000"),
                Map.entry("idempotencyKey", "request-key"),
                Map.entry("idempotencyRequestHash", "f5ed760a79e8a5335e5ad28cc5db6ba5059f453d5209e426f54f5308e092735b"),
                Map.entry("currentPrice", "12000"),
                Map.entry("bidCount", "2"),
                Map.entry("closeTime", "2026-08-10T12:00:00Z"),
                Map.entry("auctionStatus", "OPEN"),
                Map.entry("occurredAt", "2026-08-10T11:00:00Z")
        ));
    }
}
