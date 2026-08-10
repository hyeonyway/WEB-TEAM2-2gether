package com.dbidding.notification.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Redis 채널을 구독해 역직렬화한 뒤 그대로 로컬 커넥션에 push한다 — 이미 완성된 payload를
 * 전달만 하므로 DB 조회 없음. origin(저장+발행)용 {@code notificationTaskExecutor}를 그대로
 * 재사용한다 — 이번엔 origin/subscriber executor를 분리하지 않는다(#305에서 가상 스레드
 * 전환 시 같이 분리). {@code local-sse} 프로필에서는 Redis를 안 쓰므로 비활성화.
 */
@Component
@Profile("!local-sse")
@RequiredArgsConstructor
@Slf4j
public class NotificationPushRedisSubscriber implements MessageListener {
    private final NotificationSseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    @Async("notificationTaskExecutor")
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            NotificationPushMessage parsed = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), NotificationPushMessage.class);
            connectionManager.push(parsed.userId(), parsed.payload());
        } catch (JsonProcessingException exception) {
            log.warn("event=notification.sse.redis_subscriber.deserialize_failed", exception);
        }
    }
}
