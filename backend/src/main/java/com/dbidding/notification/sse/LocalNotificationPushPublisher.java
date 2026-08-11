package com.dbidding.notification.sse;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Redis 없이 이 인스턴스의 로컬 커넥션에 직접 push한다(#346) — 단일 인스턴스 로컬 개발 등
 * Redis 없는 환경에서 {@code local-sse} 프로필로 켠다. 기본값은 {@link RedisNotificationPushPublisher}.
 */
@Component
@Profile("local-sse")
@RequiredArgsConstructor
public class LocalNotificationPushPublisher implements NotificationPushPublisher {
    private final NotificationSseConnectionManager connectionManager;

    @Override
    public void publish(List<NotificationPushMessage> messages) {
        messages.forEach(message -> connectionManager.push(message.userId(), message.payload()));
    }
}
