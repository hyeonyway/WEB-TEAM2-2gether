package com.dbidding.notification.recovery.scheduler;

import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경매 종료(낙찰/유찰) 알림 유실 복구. 순수 결과 통보라 늦어도 유저가 할 수 있는 게 없으므로
 * UrgentNotificationRecoveryScheduler보다 느긋한 주기로 돈다.
 * 설계 근거: docs/hamin/notification/6-notification-recovery-batch.md 결정 5
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notification.recovery.result.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AuctionResultNotificationRecoveryScheduler {

    private final NotificationReconciliationService notificationReconciliationService;
    private final Clock clock;

    // 7분에 한번 -> 윈도우 20분
    @Scheduled(
            fixedDelayString = "${notification.recovery.result.fixed-delay-ms:420000}",
            scheduler = "notificationRecoveryTaskScheduler"
    )
    public void recover() {
        Instant now = clock.instant();
        Instant auctionClosedWindowStart = now.minus(Duration.ofMinutes(20));
        try {
            notificationReconciliationService.recoverAuctionClosedNotifications(auctionClosedWindowStart);
        } catch (RuntimeException exception) {
            log.error("event=notification.recovery.result.failed now={}", now, exception);
            throw exception;
        }
    }
}
