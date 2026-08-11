package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * {@code sse-virtual-threads} 프로필 — 유저 1명당 독립 task로 세분화한다(#362).
 * 호출자의 배치 단위 hop은 그대로 두고(가상 스레드라 중첩 hop 비용이 없음) 여기서
 * 한 번 더 개별 task로 쪼갠다 — 느린 클라이언트 하나가 같은 배치의 다른 유저 전송을
 * 막지 않게 하는 게 목적.
 */
@Component
@Profile("sse-virtual-threads")
@RequiredArgsConstructor
public class VirtualThreadNotificationPushDispatcher implements NotificationPushDispatcher {
    private final NotificationSseConnectionManager connectionManager;

    @Async("notificationFanOutTaskExecutor")
    @Override
    public void dispatch(Integer userId, NotificationResponse payload) {
        connectionManager.push(userId, payload);
    }
}
