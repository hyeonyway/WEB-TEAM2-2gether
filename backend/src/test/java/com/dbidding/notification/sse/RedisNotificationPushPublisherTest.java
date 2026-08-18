package com.dbidding.notification.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisNotificationPushPublisherTest {

    @Test
    void 단건_publish는_원소_1개짜리_배열로_감싸서_지정된_채널로_publish한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        RedisNotificationPushPublisher publisher = new RedisNotificationPushPublisher(redisTemplate, objectMapper);
        NotificationResponse payload = new NotificationResponse(
                1L, 10, NotificationType.OUTBID, 5L, "상회 입찰 발생", false, Instant.parse("2026-08-10T00:00:00Z"));

        publisher.publish(7, payload);

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(NotificationPushPublisher.CHANNEL), messageCaptor.capture());
        var json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json).hasSize(1);
        assertThat(json.get(0).get("userId").asInt()).isEqualTo(7);
        assertThat(json.get(0).get("payload").get("id").asLong()).isEqualTo(1L);
        assertThat(json.get(0).get("payload").get("message").asText()).isEqualTo("상회 입찰 발생");
    }

    @Test
    void 여러_유저를_묶어_publish하면_Redis_PUBLISH를_1번만_호출한다() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        RedisNotificationPushPublisher publisher = new RedisNotificationPushPublisher(redisTemplate, objectMapper);
        NotificationResponse payload1 = new NotificationResponse(
                1L, 10, NotificationType.AUCTION_OPENED, 0L, "리자몽 EX 카드의 경매가 등록되었습니다.", false, Instant.parse("2026-08-10T00:00:00Z"));
        NotificationResponse payload2 = new NotificationResponse(
                2L, 10, NotificationType.AUCTION_OPENED, 0L, "리자몽 EX 카드의 경매가 등록되었습니다.", false, Instant.parse("2026-08-10T00:00:00Z"));

        publisher.publish(List.of(new NotificationPushMessage(7, payload1), new NotificationPushMessage(8, payload2)));

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisTemplate).convertAndSend(eq(NotificationPushPublisher.CHANNEL), messageCaptor.capture());
        var json = objectMapper.readTree(messageCaptor.getValue());
        assertThat(json).hasSize(2);
        assertThat(json.get(0).get("userId").asInt()).isEqualTo(7);
        assertThat(json.get(0).get("payload").get("id").asLong()).isEqualTo(1L);
        assertThat(json.get(1).get("userId").asInt()).isEqualTo(8);
        assertThat(json.get(1).get("payload").get("id").asLong()).isEqualTo(2L);
    }
}
