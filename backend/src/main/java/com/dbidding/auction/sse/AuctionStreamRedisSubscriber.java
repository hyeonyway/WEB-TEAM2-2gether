package com.dbidding.auction.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis 채널을 구독해 역직렬화한 뒤 그대로 로컬 커넥션에 broadcast한다 — DB 조회 없음.
 * {@code AuctionSseConnectionManager.broadcast()} 자체가 이미 {@code @Async("auctionSseBroadcastTaskExecutor")}라
 * 별도로 executor를 지정하지 않는다. 이 executor는 send용 캡(#507)과 무관해 이 메서드를
 * 호출하는 Redis pub/sub 스레드가 캡 때문에 블로킹되지 않는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuctionStreamRedisSubscriber implements MessageListener {
    private final AuctionSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            AuctionStreamMessage parsed = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), AuctionStreamMessage.class);
            connectionManager.broadcast(parsed.payload().withType(parsed.type()));
        } catch (JsonProcessingException exception) {
            log.warn("event=auction.sse.redis_subscriber.deserialize_failed", exception);
        }
    }
}
