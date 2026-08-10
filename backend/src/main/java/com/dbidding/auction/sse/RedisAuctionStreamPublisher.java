package com.dbidding.auction.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisAuctionStreamPublisher implements AuctionStreamPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(AuctionStreamPayload payload) {
        redisTemplate.convertAndSend(CHANNEL, writeJson(new AuctionStreamMessage(payload.type(), payload)));
    }

    private String writeJson(AuctionStreamMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AuctionStreamMessage 직렬화 실패", exception);
        }
    }
}
