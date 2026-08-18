package com.dbidding.notification.sse;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

class NotificationPushRedisSubscriberTest {

    @Test
    void 수신한_메시지를_역직렬화해서_그대로_디스패처에_넘긴다() throws Exception {
        NotificationPushDispatcher pushDispatcher = mock(NotificationPushDispatcher.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        NotificationPushRedisSubscriber subscriber = new NotificationPushRedisSubscriber(pushDispatcher, objectMapper);
        NotificationResponse payload = new NotificationResponse(
                1L, 10, NotificationType.OUTBID, 5L, "상회 입찰 발생", false, Instant.parse("2026-08-10T00:00:00Z"));
        byte[] body = objectMapper.writeValueAsBytes(List.of(new NotificationPushMessage(7, payload)));
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        subscriber.onMessage(message, null);

        verify(pushDispatcher).dispatch(eq(7), eq(payload));
    }

    @Test
    void 배치_메시지를_수신하면_원소마다_디스패처에_넘긴다() throws Exception {
        NotificationPushDispatcher pushDispatcher = mock(NotificationPushDispatcher.class);
        JsonMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build();
        NotificationPushRedisSubscriber subscriber = new NotificationPushRedisSubscriber(pushDispatcher, objectMapper);
        NotificationResponse payload1 = new NotificationResponse(
                1L, 10, NotificationType.AUCTION_OPENED, 0L, "리자몽 EX 카드의 경매가 등록되었습니다.", false, Instant.parse("2026-08-10T00:00:00Z"));
        NotificationResponse payload2 = new NotificationResponse(
                2L, 10, NotificationType.AUCTION_OPENED, 0L, "리자몽 EX 카드의 경매가 등록되었습니다.", false, Instant.parse("2026-08-10T00:00:00Z"));
        byte[] body = objectMapper.writeValueAsBytes(List.of(
                new NotificationPushMessage(7, payload1), new NotificationPushMessage(8, payload2)));
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body);

        subscriber.onMessage(message, null);

        verify(pushDispatcher).dispatch(eq(7), eq(payload1));
        verify(pushDispatcher).dispatch(eq(8), eq(payload2));
    }
}
