package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;

/**
 * {@code NotificationSseConnectionManager.push()}를 호출 스레드에서 동기로 실행할지,
 * 유저 1명당 독립 task로 세분화할지를 프로필({@code sse-virtual-threads})에 따라
 * 결정한다(#362).
 */
public interface NotificationPushDispatcher {
    void dispatch(Integer userId, NotificationResponse payload);
}
