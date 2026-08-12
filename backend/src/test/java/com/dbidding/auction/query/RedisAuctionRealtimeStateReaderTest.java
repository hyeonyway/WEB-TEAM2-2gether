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
    void 활성_경매_상태와_최근_입찰과_내_입찰을_Redis에서_읽는다() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForStream()).thenReturn(streamOperations);
        when(hashOperations.entries("auction:state:10")).thenReturn(Map.ofEntries(
                Map.entry("status", "ENDING"), Map.entry("sellerId", "1"), Map.entry("itemId", "10"), Map.entry("cardName", "리자몽"),
                Map.entry("cardSetName", "base"),
                Map.entry("cardPsaGrade", "10"), Map.entry("cardLanguage", "JP"), Map.entry("cardThumbnailUrl", "/cards/charizard.png"),
                Map.entry("auctionName", "경매"), Map.entry("description", "설명"), Map.entry("psaVerified", "false"), Map.entry("startPrice", "40000"),
                Map.entry("currentPrice", "43000"), Map.entry("bidIncrement", "3000"), Map.entry("bidCount", "7"), Map.entry("deliveryFee", "3000"),
                Map.entry("imagePaths", "/auctions/1.png"), Map.entry("openTime", "2026-08-10T00:00:00Z"),
                Map.entry("closeTime", "2026-08-10T01:05:00Z"), Map.entry("buyNowPrice", "100000"), Map.entry("highestBidderId", "2")
        ));
        when(hashOperations.entries("auction:bidder:10:2")).thenReturn(Map.of("status", "LEADING", "amount", "43000"));
        MapRecord<String, Object, Object> bid = MapRecord.create("auction:recent-bids:10", Map.of(
                "bidderId", "2", "bidPrice", "43000", "sequence", "7", "occurredAt", "2026-08-10T00:00:00Z"
        ));
        when(streamOperations.reverseRange(eq("auction:recent-bids:10"), any(), any()))
                .thenReturn(List.of(bid));

        var state = new RedisAuctionRealtimeStateReader(redisTemplate).read(10, 2);

        assertThat(state.status().name()).isEqualTo("ENDING");
        assertThat(state.currentPrice()).isEqualTo(43_000L);
        assertThat(state.myBidStatus().name()).isEqualTo("LEADING");
        assertThat(state.myBidAmount()).isEqualTo(43_000L);
        assertThat(state.recentBids()).extracting(item -> item.amount()).containsExactly(43_000L);
        assertThat(state.recentBids()).extracting(item -> item.id()).containsExactly(Long.MAX_VALUE - 7);
        assertThat(state.recentBids()).extracting(item -> item.isHighest()).containsExactly(true);
        assertThat(new RedisAuctionRealtimeStateReader(redisTemplate).readAuctionState(10).cardSetName()).isEqualTo("base");
    }
}
