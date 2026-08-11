package com.dbidding.notification.sse;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Redis 없이 이 인스턴스의 로컬 커넥션에 직접 push한다(#346) — 단일 인스턴스 로컬 개발 등
 * Redis 없는 환경에서 {@code local-sse} 프로필로 켠다. 기본값은 {@link RedisNotificationPushPublisher}.
 * subscriber(로컬 fan-out) 전용 {@code notificationFanOutTaskExecutor}를 써서, origin(호출자인
 * {@code NotificationEventListener})의 async 컨텍스트에 얹혀 실행되지 않도록 분리한다(#305).
 * 클래스 레벨에 {@code @Async}를 건다 — {@code NotificationPushPublisher.publish(Integer, NotificationResponse)}는
 * 이 클래스가 오버라이드하지 않는 default 메서드라, 메서드 레벨로 걸면 그 안에서 호출하는
 * {@code this.publish(List)}가 self-invocation이라 프록시를 안 타서 적용되지 않는다.
 * 유저별 push를 동기로 할지 개별 task로 세분화할지는 {@link NotificationPushDispatcher}가
 * 프로필({@code sse-virtual-threads})에 따라 결정한다(#362).
 */
@Component
@Profile("local-sse")
@RequiredArgsConstructor
@Async("notificationFanOutTaskExecutor")
public class LocalNotificationPushPublisher implements NotificationPushPublisher {
    private final NotificationPushDispatcher pushDispatcher;

    @Override
    public void publish(List<NotificationPushMessage> messages) {
        messages.forEach(message -> pushDispatcher.dispatch(message.userId(), message.payload()));
    }
}
