package com.dbidding.notification.sse;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class NotificationPushRedisSubscriberTest {

    @Test
    void 수신한_메시지를_역직렬화해서_그대로_로컬_커넥션에_push한다() throws Exception {
        NotificationSseConnectionManager connectionManager = mock(NotificationSseConnectionManager.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        NotificationPushRedisSubscriber subscriber = new NotificationPushRedisSubscriber(connectionManager, objectMapper);
        NotificationResponse payload = new NotificationResponse(
                1L, 10, NotificationType.OUTBID, "상회 입찰 발생", false, Instant.parse("2026-08-10T00:00:00Z"));
        byte[] body = objectMapper.writeValueAsBytes(new NotificationPushMessage(7, payload));
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        subscriber.onMessage(message, null);

        verify(connectionManager).push(eq(7), eq(payload));
    }
}
