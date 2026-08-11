package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("!local-sse")
@RequiredArgsConstructor
public class RedisNotificationPushPublisher implements NotificationPushPublisher {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(Integer userId, NotificationResponse payload) {
        redisTemplate.convertAndSend(CHANNEL, writeJson(new NotificationPushMessage(userId, payload)));
    }

    private String writeJson(NotificationPushMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("NotificationPushMessage 직렬화 실패", exception);
        }
    }
}
