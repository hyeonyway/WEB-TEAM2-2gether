package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;

/**
 * 알림 저장은 이벤트를 처리한 인스턴스에서 그대로 하고, 이 인터페이스는 "push를 발행"만
 * 담당한다. 지금은 인스턴스가 하나뿐이라 로컬 구현체가 즉시 직접 전송하지만, 다중 인스턴스로
 * 확장되면 Redis Pub/Sub 등으로 교체해 모든 인스턴스가 발행을 받아 자기가 들고 있는 연결에만
 * 전송하게 만들 수 있다.
 */
public interface NotificationPushPublisher {
    void publish(Integer userId, NotificationResponse payload);
}
