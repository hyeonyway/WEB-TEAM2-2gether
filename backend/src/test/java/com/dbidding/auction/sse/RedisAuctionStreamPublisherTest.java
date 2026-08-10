package com.dbidding.auction.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.BidPlacedEvent;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisAuctionStreamPublisherTest {

    @Test
    void payload를_type과_함께_감싸서_지정된_채널로_publish한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        RedisAuctionStreamPublisher publisher = new RedisAuctionStreamPublisher(redisTemplate, objectMapper);
        BidPlacedEvent event = new BidPlacedEvent(
                10, 1, 7, 5, 20L, 40_000L, 50_000L, 1_000L, 2,
                Instant.parse("2026-08-10T01:00:00Z"), AuctionStatus.OPEN, Instant.parse("2026-08-10T00:00:00Z"));

        publisher.publish(AuctionStreamPayload.bidPlaced(event));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(AuctionStreamPublisher.CHANNEL), messageCaptor.capture());
        var json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json.get("type").asText()).isEqualTo("BID_PLACED");
        assertThat(json.get("payload").get("auction_id").asInt()).isEqualTo(10);
        assertThat(json.get("payload").get("bidder_id").asInt()).isEqualTo(7);
        assertThat(json.get("payload").has("type")).isFalse();
    }
}
