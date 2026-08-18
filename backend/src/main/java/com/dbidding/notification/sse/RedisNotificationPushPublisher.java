package com.dbidding.notification.sse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisNotificationPushPublisher implements NotificationPushPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(List<NotificationPushMessage> messages) {
        redisTemplate.convertAndSend(CHANNEL, writeJson(messages));
    }

    private String writeJson(List<NotificationPushMessage> messages) {
        try {
            return objectMapper.writeValueAsString(messages);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("NotificationPushMessage 직렬화 실패", exception);
        }
    }
}
