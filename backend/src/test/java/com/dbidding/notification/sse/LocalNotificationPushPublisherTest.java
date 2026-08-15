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
    void 단건_publish는_디스패처로_그대로_위임한다() {
        NotificationPushDispatcher pushDispatcher = mock(NotificationPushDispatcher.class);
        LocalNotificationPushPublisher publisher = new LocalNotificationPushPublisher(pushDispatcher);
        NotificationResponse payload = new NotificationResponse(1L, 100, NotificationType.AUCTION_OPENED, 0L, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));

        publisher.publish(1, payload);

        verify(pushDispatcher).dispatch(1, payload);
    }

    @Test
    void 배치_publish는_원소마다_디스패처를_호출한다() {
        NotificationPushDispatcher pushDispatcher = mock(NotificationPushDispatcher.class);
        LocalNotificationPushPublisher publisher = new LocalNotificationPushPublisher(pushDispatcher);
        NotificationResponse payload1 = new NotificationResponse(1L, 100, NotificationType.AUCTION_OPENED, 0L, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));
        NotificationResponse payload2 = new NotificationResponse(2L, 100, NotificationType.AUCTION_OPENED, 0L, "메시지", false, Instant.parse("2026-07-30T12:00:00Z"));

        publisher.publish(List.of(new NotificationPushMessage(1, payload1), new NotificationPushMessage(2, payload2)));

        verify(pushDispatcher).dispatch(1, payload1);
        verify(pushDispatcher).dispatch(2, payload2);
    }
}
