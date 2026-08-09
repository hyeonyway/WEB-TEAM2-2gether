package com.dbidding.notification.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.dto.NotificationResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalNotificationPushPublisherTest {

    @Test
    void publish는_연결_관리자의_push로_그대로_위임한다() {
        NotificationSseConnectionManager connectionManager = mock(NotificationSseConnectionManager.class);
        LocalNotificationPushPublisher publisher = new LocalNotificationPushPublisher(connectionManager);
        NotificationResponse payload = new NotificationResponse(1L, 100, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));

        publisher.publish(1, payload);

        verify(connectionManager).push(1, payload);
    }
}
