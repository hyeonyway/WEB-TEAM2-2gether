package com.dbidding.auction.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuctionResponseJsonTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void 경매_목록_응답은_snake_case로_직렬화된다() throws Exception {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        var summary = new AuctionResponses.AuctionSummary(
                1,
                new AuctionResponses.CardSummary(2, "카드", "세트", "10", "JP", "/card.png"),
                new AuctionResponses.SellerSummary(3, "seller", 4, 95),
                10_000L,
                12_000L,
                1_000L,
                13_000L,
                2,
                20_000L,
                now,
                now.plusSeconds(3600),
                AuctionStatus.OPEN,
                MyBidStatus.LEADING,
                12_000L
        );
        var response = new AuctionResponses.CursorPage<>(List.of(summary), "next-token", true, 20);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.has("next_cursor")).isTrue();
        assertThat(json.has("has_next")).isTrue();
        assertThat(json.has("page")).isFalse();
        assertThat(json.has("size")).isFalse();
        assertThat(json.path("total_elements").asLong()).isEqualTo(20);
        JsonNode item = json.path("content").get(0);
        assertThat(item.has("current_price")).isTrue();
        assertThat(item.has("minimum_bid")).isTrue();
        assertThat(item.path("buy_now_price").asLong()).isEqualTo(20_000L);
        assertThat(item.has("my_bid_status")).isTrue();
        assertThat(item.path("card").has("thumbnail_url")).isTrue();
        assertThat(item.path("seller").has("trust_score")).isTrue();
    }

    @Test
    void 입찰_응답은_입찰_경매_지갑_스냅샷을_snake_case로_직렬화한다() {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        var response = new BidResponses.BidResult(
                new BidResponses.BidDetail(10L, 13_000L, BidStatus.LEADING, now),
                new BidResponses.AuctionSnapshot(1, 13_000L, 14_000L, 3, now.plusSeconds(3600)),
                new BidResponses.WalletSummary(87_000L, 13_000L)
        );

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("bid").has("created_at")).isTrue();
        assertThat(json.path("auction").has("current_price")).isTrue();
        assertThat(json.path("auction").has("minimum_bid")).isTrue();
        assertThat(json.path("auction").has("bid_count")).isTrue();
        assertThat(json.path("auction").has("ends_at")).isTrue();
        assertThat(json.path("wallet").has("available_balance")).isTrue();
        assertThat(json.path("wallet").has("frozen_balance")).isTrue();
    }

    @Test
    void 경매_생성_응답은_snake_case로_직렬화된다() {
        Instant now = Instant.parse("2026-07-30T12:00:00Z");
        JsonNode created = objectMapper.valueToTree(
                new AuctionCreateResponse(1, AuctionStatus.OPEN, now, now.plusSeconds(3600))
        );

        assertThat(created.has("starts_at")).isTrue();
        assertThat(created.has("ends_at")).isTrue();
    }
}
