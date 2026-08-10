package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LocalNotificationPushPublisher implements NotificationPushPublisher {
    private final NotificationSseConnectionManager connectionManager;

    @Override
    public void publish(Integer userId, NotificationResponse payload) {
        connectionManager.push(userId, payload);
    }
}
