package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;

/**
 * 알림 저장은 이벤트를 처리한 인스턴스에서 그대로 하고, 이 인터페이스는 "push를 발행"만
 * 담당한다(#281). Redis Pub/Sub으로 모든 인스턴스가 발행을 받아 자기가 들고 있는 연결에만
 * 전송한다.
 */
public interface NotificationPushPublisher {
    String CHANNEL = "notification:push";

    void publish(Integer userId, NotificationResponse payload);
}
