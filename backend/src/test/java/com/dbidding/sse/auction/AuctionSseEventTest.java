package com.dbidding.sse.auction;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.sse.auction.payload.AuctionClosedPayload;
import com.dbidding.sse.auction.payload.AuctionCreatedPayload;
import com.dbidding.sse.auction.payload.AuctionPayloadStatus;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class AuctionSseEventTest {
    private final JsonMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);

    @Test
    void 생성_이벤트를_전체_렌더링_payload로_직렬화한다() throws Exception {
        var payload = new AuctionCreatedPayload(
                null, 10, 20, "리자몽", "10", "JP", "/card.png", 3,
                40_000L, 40_000L, 1_000L, 0, now.plusHours(1),
                AuctionPayloadStatus.OPEN, 1L, now
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(payload));

        assertThat(json.get("type").asText()).isEqualTo("AUCTION_CREATED");
        assertThat(json.get("auction_id").asInt()).isEqualTo(10);
        assertThat(json.get("card_name").asText()).isEqualTo("리자몽");
        assertThat(json.get("current_price").asLong()).isEqualTo(40_000L);
        assertThat(json.get("auction_version").asLong()).isEqualTo(1L);
    }

    @Test
    void 입찰_이벤트에_현재가와_이전_입찰자를_포함한다() throws Exception {
        var payload = new BidPlacedPayload(
                null, 10, 7, 5,
                40_000L, 50_000L, 1_000L, 2, now.plusHours(1),
                AuctionPayloadStatus.OPEN, 2L, now
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(payload));

        assertThat(json.get("type").asText()).isEqualTo("BID_PLACED");
        assertThat(json.get("bidder_id").asInt()).isEqualTo(7);
        assertThat(json.get("previous_bidder_id").asInt()).isEqualTo(5);
        assertThat(json.get("current_price").asLong()).isEqualTo(50_000L);
        assertThat(json.has("card_id")).isFalse();
        assertThat(json.has("seller_id")).isFalse();
        assertThat(json.has("bid_price")).isFalse();
    }

    @Test
    void 유찰_종료_이벤트의_winner는_null이다() throws Exception {
        var payload = new AuctionClosedPayload(
                null, 10, 20, "리자몽", "10", "JP", "/card.png", null, 3,
                40_000L, 40_000L, 1_000L, 0, now, AuctionPayloadStatus.ENDED, 2L,
                now, now
        );

        var json = objectMapper.readTree(objectMapper.writeValueAsBytes(payload));

        assertThat(json.get("type").asText()).isEqualTo("AUCTION_CLOSED");
        assertThat(json.get("winner_id").isNull()).isTrue();
        assertThat(json.get("final_price").asLong()).isEqualTo(40_000L);
    }
}
