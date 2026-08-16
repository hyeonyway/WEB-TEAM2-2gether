package com.dbidding.notification.recovery.scheduler;

import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 긴급하지 않은 알림 유실 복구 묶음: 경매 생성, 경매 종료(낙찰/유찰), OPEN 경매의 상회 입찰.
 * 셋 다 늦어도 유저가 재입찰 기회를 잃거나 하지 않으므로(OPEN은 마감까지 여유가 있고, 경매
 * 생성/종료는 순수 정보성) UrgentNotificationRecoveryScheduler(ENDING 상회 입찰 전용)보다
 * 느긋한 주기로 돈다. 원래는 경매 종료만 담당하던 AuctionResultNotificationRecoveryScheduler
 * 였으나 이슈 #373에서 범위가 넓어져 이름/프로퍼티 키까지 함께 정리했다.
 * 설계 근거: docs/hamin/notification/6-notification-recovery-batch.md 결정 5,
 * docs/hamin/notification/18-recovery-scheduler-urgency-split-plan.md
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notification.recovery.non-urgent.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NonUrgentNotificationRecoveryScheduler {

    private final NotificationReconciliationService notificationReconciliationService;
    private final Clock clock;

    // 5분에 한번 -> 경매 생성/상회입찰 윈도우 15분(3배), 경매 종료 윈도우 20분(4배, 기존 유지)
    @Scheduled(
            fixedDelayString = "${notification.recovery.non-urgent.fixed-delay-ms:300000}",
            scheduler = "notificationRecoveryTaskScheduler"
    )
    @SchedulerLock(
            name = "notification-recovery-non-urgent",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT10M"
    )
    public void recover() {
        Instant now = clock.instant();
        Instant recentWindowStart = now.minus(Duration.ofMinutes(15));
        Instant auctionClosedWindowStart = now.minus(Duration.ofMinutes(20));
        try {
            notificationReconciliationService.recoverAuctionOpenedNotifications(recentWindowStart);
            notificationReconciliationService.recoverAuctionClosedNotifications(auctionClosedWindowStart);
            notificationReconciliationService.recoverOpenOutbidNotifications(recentWindowStart);
        } catch (RuntimeException exception) {
            log.error("event=notification.recovery.non-urgent.failed now={}", now, exception);
            throw exception;
        }
    }
}
