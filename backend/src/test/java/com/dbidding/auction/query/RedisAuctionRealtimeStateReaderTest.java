package com.dbidding.auction.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class RedisAuctionRealtimeStateReaderTest {
    @Mock private StringRedisTemplate redisTemplate;
    @Mock private HashOperations<String, Object, Object> hashOperations;
    @Mock private StreamOperations<String, Object, Object> streamOperations;

    @Test
    void 지수_표기된_활성_경매와_최근_입찰_금액을_Redis에서_읽는다() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(hashOperations.entries("auction:state:10")).thenReturn(Map.ofEntries(
                Map.entry("status", "ENDING"), Map.entry("sellerId", "1"), Map.entry("itemId", "10"), Map.entry("cardName", "리자몽"),
                Map.entry("cardSetName", "base"),
                Map.entry("cardPsaGrade", "10"), Map.entry("cardLanguage", "JP"), Map.entry("cardThumbnailUrl", "/cards/charizard.png"),
                Map.entry("auctionName", "경매"), Map.entry("description", "설명"), Map.entry("psaVerified", "false"), Map.entry("startPrice", "4.0000e+4"),
                Map.entry("currentPrice", "4.3000e+4"), Map.entry("bidIncrement", "3.000e+3"), Map.entry("bidCount", "7"), Map.entry("deliveryFee", "3.000e+3"),
                Map.entry("imagePaths", "/auctions/1.png"), Map.entry("openTime", "2026-08-10T00:00:00Z"),
                Map.entry("closeTime", "2026-08-10T01:05:00Z"), Map.entry("buyNowPrice", "1.00000e+5"), Map.entry("highestBidderId", "2")
        ));
        when(hashOperations.entries("auction:bidder:10:2")).thenReturn(Map.of("status", "LEADING", "amount", "4.3000e+4"));
        MapRecord<String, Object, Object> bid = MapRecord.create("auction:recent-bids:10", Map.of(
                "bidderId", "2", "bidPrice", "4.3000e+4", "sequence", "7e+0", "occurredAt", "2026-08-10T00:00:00Z"
        ));
        when(streamOperations.reverseRange(eq("auction:recent-bids:10"), any(), any()))
                .thenReturn(List.of(bid));

        var state = new RedisAuctionRealtimeStateReader(redisTemplate).read(10, 2);

        assertThat(state.status().name()).isEqualTo("ENDING");
        assertThat(state.currentPrice()).isEqualTo(43_000L);
        assertThat(state.myBidStatus().name()).isEqualTo("LEADING");
        assertThat(state.myBidAmount()).isEqualTo(43_000L);
        assertThat(state.recentBids()).extracting(item -> item.amount()).containsExactly(43_000L);
        assertThat(state.recentBids()).extracting(item -> item.id()).containsExactly(-7L);
        assertThat(state.recentBids()).extracting(item -> item.isHighest()).containsExactly(true);
        var auction = new RedisAuctionRealtimeStateReader(redisTemplate).readAuctionState(10);
        assertThat(auction.cardSetName()).isEqualTo("base");
        assertThat(auction.startPrice()).isEqualTo(40_000L);
        assertThat(auction.buyNowPrice()).isEqualTo(100_000L);
        assertThat(auction.deliveryFee()).isEqualTo(3_000L);
    }

    @Test
    void 실시간_입찰_id는_JS_Number_정밀도_범위_안에_있어_서로_다른_입찰끼리_겹치지_않는다() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(hashOperations.entries("auction:state:10")).thenReturn(Map.ofEntries(
                Map.entry("status", "ENDING"), Map.entry("sellerId", "1"), Map.entry("itemId", "10"), Map.entry("cardName", "리자몽"),
                Map.entry("cardSetName", "base"),
                Map.entry("cardPsaGrade", "10"), Map.entry("cardLanguage", "JP"), Map.entry("cardThumbnailUrl", "/cards/charizard.png"),
                Map.entry("auctionName", "경매"), Map.entry("description", "설명"), Map.entry("psaVerified", "false"), Map.entry("startPrice", "40000"),
                Map.entry("currentPrice", "43000"), Map.entry("bidIncrement", "3000"), Map.entry("bidCount", "300"), Map.entry("deliveryFee", "3000"),
                Map.entry("imagePaths", "/auctions/1.png"), Map.entry("openTime", "2026-08-10T00:00:00Z"),
                Map.entry("closeTime", "2026-08-10T01:05:00Z"), Map.entry("buyNowPrice", "100000"), Map.entry("highestBidderId", "2")
        ));
        when(hashOperations.entries("auction:bidder:10:2")).thenReturn(Map.of("status", "LEADING", "amount", "43000"));
        // 부하 상황에서 sequence 몇백 개 차이 나는 두 입찰. Long.MAX_VALUE 근처 double 표현 간격(약 2048)보다
        // 작은 차이라, 예전 계산식(Long.MAX_VALUE - sequence)이었다면 JSON 직렬화 후 JS Number로는
        // 같은 값으로 뭉개져 React key 충돌(중복 렌더링)로 이어졌다.
        MapRecord<String, Object, Object> older = MapRecord.create("auction:recent-bids:10", Map.of(
                "bidderId", "2", "bidPrice", "43000", "sequence", "1", "occurredAt", "2026-08-10T00:00:00Z"
        ));
        MapRecord<String, Object, Object> newer = MapRecord.create("auction:recent-bids:10", Map.of(
                "bidderId", "2", "bidPrice", "46000", "sequence", "300", "occurredAt", "2026-08-10T00:01:00Z"
        ));
        when(streamOperations.reverseRange(eq("auction:recent-bids:10"), any(), any()))
                .thenReturn(List.of(newer, older));

        var state = new RedisAuctionRealtimeStateReader(redisTemplate).read(10, 2);

        assertThat(state.recentBids()).extracting(item -> item.id()).containsExactly(-300L, -1L);
        assertThat(state.recentBids()).extracting(RedisAuctionRealtimeStateReaderTest::withinJsSafeIntegerRange)
                .containsOnly(true);
    }

    private static boolean withinJsSafeIntegerRange(com.dbidding.auction.dto.BidResponses.BidSummary summary) {
        return Math.abs(summary.id()) <= 9_007_199_254_740_991L;
    }
}
