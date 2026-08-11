package com.dbidding.notification.sse;

import com.dbidding.notification.dto.NotificationResponse;
import java.util.List;

/**
 * 알림 저장은 이벤트를 처리한 인스턴스에서 그대로 하고, 이 인터페이스는 "push를 발행"만
 * 담당한다(#281). Redis Pub/Sub으로 모든 인스턴스가 발행을 받아 자기가 들고 있는 연결에만
 * 전송한다. 위시리스트 fan-out처럼 대상이 여럿이면 {@link #publish(List)}로 한 번에 묶어
 * 발행해, 유저 수와 무관하게 발행 호출 자체는 1번으로 고정한다(#289).
 */
public interface NotificationPushPublisher {
    String CHANNEL = "notification:push";

    default void publish(Integer userId, NotificationResponse payload) {
        publish(List.of(new NotificationPushMessage(userId, payload)));
    }

    void publish(List<NotificationPushMessage> messages);
}
