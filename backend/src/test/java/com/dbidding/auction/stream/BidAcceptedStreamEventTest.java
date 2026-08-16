package com.dbidding.auction.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BidAcceptedStreamEventTest {

    @Test
    void v2_충전_이벤트는_사후_잔액과_버전을_역직렬화한다() {
        UUID eventId = UUID.randomUUID();
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from("1720000000000-3", Map.ofEntries(
                Map.entry("schemaVersion", "2"),
                Map.entry("eventType", "wallet.charged.v1"),
                Map.entry("eventId", eventId.toString()),
                Map.entry("userId", "20"),
                Map.entry("walletVersion", "8"),
                Map.entry("availableBalance", "70000"),
                Map.entry("frozenBalance", "10000"),
                Map.entry("transactionType", "CHARGE"),
                Map.entry("transactionAmount", "3000"),
                Map.entry("idempotencyKey", "charge-1"),
                Map.entry("occurredAt", "2026-08-11T00:00:00Z")
        ));

        assertThat(event).isInstanceOf(WalletStateChangedStreamEvent.class);
        WalletStateChangedStreamEvent wallet = (WalletStateChangedStreamEvent) event;
        assertThat(wallet.eventId()).isEqualTo(eventId);
        assertThat(wallet.walletVersion()).isEqualTo(8L);
        assertThat(wallet.availableBalance()).isEqualTo(70_000L);
        assertThat(wallet.frozenBalance()).isEqualTo(10_000L);
    }

    @Test
    void 경매_생성_stream_계약을_파싱한다() {
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from("1720000000000-4", Map.ofEntries(
                Map.entry("eventType", "auction.created.v1"), Map.entry("schemaVersion", "1"),
                Map.entry("auctionId", "42"), Map.entry("sellerId", "1"), Map.entry("itemId", "10"), Map.entry("auctionName", "name"),
                Map.entry("description", "description"), Map.entry("startPrice", "10000"),
                Map.entry("buyNowPrice", "20000"), Map.entry("deliveryFee", "3000"),
                Map.entry("bidPriceUnit", "1000"), Map.entry("closeTime", "2026-08-10T12:00:00Z"),
                Map.entry("sellerMemo", "memo"), Map.entry("psaCertification", "1234567"),
                Map.entry("selfGrade", "NM"), Map.entry("psaVerified", "true"),
                Map.entry("imagePaths", "auctions/1.jpg\nauctions/2.jpg"),
                Map.entry("idempotencyKey", "auction-create-1"), Map.entry("idempotencyRequestHash", "a".repeat(64)),
                Map.entry("occurredAt", "2026-08-10T11:00:00Z")
        ));
        assertThat(event).isInstanceOf(AuctionCreatedStreamEvent.class);
        assertThat(((AuctionCreatedStreamEvent) event).auctionId()).isEqualTo(42);
        assertThat(((AuctionCreatedStreamEvent) event).itemId()).isEqualTo(10);
    }

    @Test
    void 경매_종료_요청_stream_계약을_파싱한다() {
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from("1720000000000-5", Map.of(
                "eventType", "auction.close-requested.v1", "schemaVersion", "1",
                "auctionId", "10", "occurredAt", "2026-08-10T12:00:00Z"
        ));
        assertThat(event).isInstanceOf(AuctionCloseRequestedStreamEvent.class);
        assertThat(((AuctionCloseRequestedStreamEvent) event).auctionId()).isEqualTo(10);
    }
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
    void 지수_표기된_입찰_Stream_정수_필드를_정확하게_파싱한다() {
        Map<String, String> fields = fields();
        fields.put("auctionVersion", "1.00000000000000e+14");
        fields.put("requestedPrice", "1.2000e+4");
        fields.put("bidPrice", "1.2000e+4");
        fields.put("currentPrice", "1.2000e+4");
        fields.put("bidCount", "2e+0");

        BidAcceptedStreamEvent event = BidAcceptedStreamEvent.from("1720000000000-0", fields);

        assertThat(event.auctionVersion()).isEqualTo(100_000_000_000_000L);
        assertThat(event.requestedPrice()).isEqualTo(12_000L);
        assertThat(event.bidPrice()).isEqualTo(12_000L);
        assertThat(event.bidCount()).isEqualTo(2);
    }

    @Test
    void 지수_표기된_지갑_Stream_정수_필드를_정확하게_파싱한다() {
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from("1720000000000-3", Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.charged.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "20"),
                Map.entry("walletVersion", "1.00000000000000e+14"),
                Map.entry("availableBalance", "7.0000e+4"), Map.entry("frozenBalance", "1.0000e+4"),
                Map.entry("transactionType", "CHARGE"), Map.entry("transactionAmount", "3.000e+3"),
                Map.entry("idempotencyKey", "charge-1"), Map.entry("occurredAt", "2026-08-11T00:00:00Z")
        ));

        WalletStateChangedStreamEvent wallet = (WalletStateChangedStreamEvent) event;
        assertThat(wallet.walletVersion()).isEqualTo(100_000_000_000_000L);
        assertThat(wallet.availableBalance()).isEqualTo(70_000L);
        assertThat(wallet.frozenBalance()).isEqualTo(10_000L);
        assertThat(wallet.transactionAmount()).isEqualTo(3_000L);
    }

    @Test
    void 지수_표기된_주문_Stream_정수_필드를_정확하게_파싱한다() {
        AuctionWalletTimelineEvent event = AuctionWalletTimelineEvent.from("1720000000000-4", Map.ofEntries(
                Map.entry("schemaVersion", "1"), Map.entry("eventType", "order.completed.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("orderId", "100"),
                Map.entry("auctionId", "10"), Map.entry("orderVersion", "1.00000000000000e+14"),
                Map.entry("actorId", "1"), Map.entry("buyerId", "1"), Map.entry("sellerId", "7"),
                Map.entry("status", "COMPLETED"), Map.entry("walletUserId", "7"),
                Map.entry("walletVersion", "1.00000000000000e+14"),
                Map.entry("availableBalance", "1.000000000000e+12"), Map.entry("frozenBalance", "0e+0"),
                Map.entry("transactionType", "ORDER_SETTLEMENT"), Map.entry("transactionAmount", "1.00000000000e+11"),
                Map.entry("idempotencyKey", "confirm:100"), Map.entry("occurredAt", "2026-08-11T00:00:00Z")
        ));

        OrderStateChangedStreamEvent order = (OrderStateChangedStreamEvent) event;
        assertThat(order.orderVersion()).isEqualTo(100_000_000_000_000L);
        assertThat(order.walletVersion()).isEqualTo(100_000_000_000_000L);
        assertThat(order.availableBalance()).isEqualTo(1_000_000_000_000L);
        assertThat(order.transactionAmount()).isEqualTo(100_000_000_000L);
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

    @Test
    void 지원하지_않는_schemaVersion은_입찰_이벤트를_거부한다() {
        Map<String, String> fields = fields();
        fields.put("schemaVersion", "2");

        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("지원하지 않는");
    }

    @Test
    void 일반_입찰의_발생시각이_마감시각과_같으면_거부한다() {
        Map<String, String> fields = fields();
        fields.put("occurredAt", fields.get("closeTime"));

        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("마감 시각보다 이전");
    }

    @Test
    void 일반_입찰의_요청가와_승인가가_다르면_거부한다() {
        Map<String, String> fields = fields();
        fields.put("requestedPrice", "11000");
        fields.put("idempotencyRequestHash", "b7c07f5860b4a2be594ae268cdf87a1efddd1b46698527a49d01742a33a6c711");

        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("원 요청가와 승인 입찰가");
    }

    @Test
    void 지갑_이벤트의_잘못된_hold_조합은_거부한다() {
        Map<String, String> fields = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.hold.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "1"), Map.entry("availableBalance", "0"), Map.entry("frozenBalance", "10000"),
                Map.entry("holdAmount", "10000"), Map.entry("occurredAt", "2026-08-10T12:00:00Z")
        ));

        assertThatThrownBy(() -> AuctionWalletTimelineEvent.from("wallet-bad", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("hold projection");
    }

    @Test
    void 지갑_이벤트의_음수_잔액은_거부한다() {
        Map<String, String> fields = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.charged.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "1"), Map.entry("availableBalance", "-1"), Map.entry("frozenBalance", "0"),
                Map.entry("occurredAt", "2026-08-10T12:00:00Z")
        ));

        assertThatThrownBy(() -> AuctionWalletTimelineEvent.from("wallet-negative", fields))
                .isInstanceOf(InvalidBidStreamEventException.class)
                .hasMessageContaining("값이 올바르지");
    }

    @Test
    void 지갑_hold_상태와_원장_상태가_모두_유효한_조합을_파싱한다() {
        WalletStateChangedStreamEvent event = WalletStateChangedStreamEvent.from("1-0", Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.hold.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "2"), Map.entry("availableBalance", "10000"), Map.entry("frozenBalance", "5000"),
                Map.entry("auctionId", "10"), Map.entry("holdAmount", "5000"), Map.entry("holdStatus", "HELD"),
                Map.entry("transactionType", "AUCTION_CAPTURE"), Map.entry("transactionAmount", "5000"),
                Map.entry("occurredAt", "2026-08-10T12:00:00Z")
        ));

        assertThat(event.auctionId()).isEqualTo(10);
        assertThat(event.holdAmount()).isEqualTo(5000L);
    }

    @Test
    void 지갑_hold금액과_hold상태의_한쪽만_존재하면_거부한다() {
        Map<String, String> fields = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.hold.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "1"), Map.entry("availableBalance", "1"), Map.entry("frozenBalance", "0"),
                Map.entry("holdAmount", "100"), Map.entry("transactionType", "AUCTION_CAPTURE"), Map.entry("transactionAmount", "100"),
                Map.entry("occurredAt", "2026-08-10T12:00:00Z")
        ));
        assertThatThrownBy(() -> WalletStateChangedStreamEvent.from("1-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class).hasMessageContaining("hold projection");
    }

    @Test
    void 지갑_transactionType과_transactionAmount의_한쪽만_존재하면_거부한다() {
        Map<String, String> fields = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.charged.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "1"), Map.entry("availableBalance", "1"), Map.entry("frozenBalance", "0"),
                Map.entry("transactionType", "CHARGE"), Map.entry("occurredAt", "2026-08-10T12:00:00Z")
        ));
        assertThatThrownBy(() -> WalletStateChangedStreamEvent.from("1-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class).hasMessageContaining("원장 field");
    }

    @Test
    void 지갑_idempotencyKey가_너무_길면_거부한다() {
        Map<String, String> fields = new java.util.HashMap<>(Map.ofEntries(
                Map.entry("schemaVersion", "2"), Map.entry("eventType", "wallet.charged.v1"),
                Map.entry("eventId", UUID.randomUUID().toString()), Map.entry("userId", "1"),
                Map.entry("walletVersion", "1"), Map.entry("availableBalance", "1"), Map.entry("frozenBalance", "0"),
                Map.entry("transactionType", "CHARGE"), Map.entry("transactionAmount", "1"),
                Map.entry("idempotencyKey", "x".repeat(65)), Map.entry("occurredAt", "2026-08-10T12:00:00Z")
        ));
        assertThatThrownBy(() -> WalletStateChangedStreamEvent.from("1-0", fields))
                .isInstanceOf(InvalidBidStreamEventException.class).hasMessageContaining("64자");
    }

    @Test
    void 입찰_계약의_주요_경계값을_거부한다() {
        Map<String, String> badStream = fields();
        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("bad", badStream)).isInstanceOf(InvalidBidStreamEventException.class);
        Map<String, String> badPrevious = fields();
        badPrevious.put("previousBidderId", "0");
        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", badPrevious)).isInstanceOf(InvalidBidStreamEventException.class);
        Map<String, String> badKey = fields();
        badKey.put("idempotencyKey", "x".repeat(65));
        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-0", badKey)).isInstanceOf(InvalidBidStreamEventException.class);
    }

    @Test
    void 즉시낙찰은_종료상태와_승인시각이_일치해야한다() {
        Map<String, String> badStatus = fields();
        badStatus.put("eventType", "auction.buy-now.v1");
        badStatus.put("auctionStatus", "OPEN");
        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-1", badStatus)).isInstanceOf(InvalidBidStreamEventException.class);
        Map<String, String> badTime = fields();
        badTime.put("eventType", "auction.buy-now.v1");
        badTime.put("auctionStatus", "ENDED");
        badTime.put("closeTime", "2026-08-10T10:00:00Z");
        assertThatThrownBy(() -> BidAcceptedStreamEvent.from("1720000000000-1", badTime)).isInstanceOf(InvalidBidStreamEventException.class);
    }

    @Test
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
