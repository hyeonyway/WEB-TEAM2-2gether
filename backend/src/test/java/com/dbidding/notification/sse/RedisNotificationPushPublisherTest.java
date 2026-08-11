package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisNotificationPushPublisherTest {

    @Test
    void userId와_payload를_감싸서_지정된_채널로_publish한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        RedisNotificationPushPublisher publisher = new RedisNotificationPushPublisher(redisTemplate, objectMapper);
        NotificationResponse payload = new NotificationResponse(
                1L, 10, NotificationType.OUTBID, "상회 입찰 발생", false, Instant.parse("2026-08-10T00:00:00Z"));

        publisher.publish(7, payload);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(NotificationPushPublisher.CHANNEL), messageCaptor.capture());
        var json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json.get("userId").asInt()).isEqualTo(7);
        assertThat(json.get("payload").get("id").asLong()).isEqualTo(1L);
        assertThat(json.get("payload").get("message").asText()).isEqualTo("상회 입찰 발생");
    }
}
