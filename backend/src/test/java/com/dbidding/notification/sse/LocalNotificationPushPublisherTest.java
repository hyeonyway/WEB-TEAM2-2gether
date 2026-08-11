package com.dbidding.notification.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.NotificationType;
import com.dbidding.notification.dto.NotificationResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalNotificationPushPublisherTest {

    @Test
    void 단건_publish는_연결_관리자의_push로_그대로_위임한다() {
        NotificationSseConnectionManager connectionManager = mock(NotificationSseConnectionManager.class);
        LocalNotificationPushPublisher publisher = new LocalNotificationPushPublisher(connectionManager);
        NotificationResponse payload = new NotificationResponse(1L, 100, NotificationType.AUCTION_OPENED, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));

        publisher.publish(1, payload);

        verify(connectionManager).push(1, payload);
    }

    @Test
    void 배치_publish는_원소마다_연결_관리자의_push를_호출한다() {
        NotificationSseConnectionManager connectionManager = mock(NotificationSseConnectionManager.class);
        LocalNotificationPushPublisher publisher = new LocalNotificationPushPublisher(connectionManager);
        NotificationResponse payload1 = new NotificationResponse(1L, 100, NotificationType.AUCTION_OPENED, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));
        NotificationResponse payload2 = new NotificationResponse(2L, 100, NotificationType.AUCTION_OPENED, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));

        publisher.publish(List.of(new NotificationPushMessage(1, payload1), new NotificationPushMessage(2, payload2)));

        verify(connectionManager).push(1, payload1);
        verify(connectionManager).push(2, payload2);
    }
}
