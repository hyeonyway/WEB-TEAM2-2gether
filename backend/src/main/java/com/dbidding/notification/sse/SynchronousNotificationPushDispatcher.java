package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 기본 프로필 — 호출 스레드에서 그대로 push한다(#362 이전 동작 그대로, 비교/롤백용
 * baseline). 호출자({@code NotificationPushRedisSubscriber.onMessage}/
 * {@code LocalNotificationPushPublisher.publish()})가 이미 배치 단위로
 * {@code notificationFanOutTaskExecutor}에 얹혀 있으므로 여기서 추가 hop이 필요 없다.
 */
@Component
@Profile("!sse-virtual-threads")
@RequiredArgsConstructor
public class SynchronousNotificationPushDispatcher implements NotificationPushDispatcher {
    private final NotificationSseConnectionManager connectionManager;

    @Override
    public void dispatch(Integer userId, NotificationResponse payload) {
        connectionManager.push(userId, payload);
    }
}
