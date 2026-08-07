package com.dbidding.notification.recovery.scheduler;

import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 경매 생성/상회 입찰 알림 유실 복구. 지연이 곧 유저 손해로 이어질 수 있는 두 타입이라
 * AuctionResultNotificationRecoveryScheduler보다 짧은 주기로 돈다.
 * 설계 근거: docs/hamin/notification/6-notification-recovery-batch.md 결정 5
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "notification.recovery.urgent.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class UrgentNotificationRecoveryScheduler {

    private final NotificationReconciliationService notificationReconciliationService;
    private final Clock clock;

    // 1분 30초에 한번 -> 10분 윈도분
    @Scheduled(
            fixedDelayString = "${notification.recovery.urgent.fixed-delay-ms:90000}",
            scheduler = "notificationRecoveryTaskScheduler"
    )
    public void recover() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime auctionOpenedWindowStart = now.minus(Duration.ofMinutes(10));
        try {
            notificationReconciliationService.recoverAuctionOpenedNotifications(auctionOpenedWindowStart);
            notificationReconciliationService.recoverOutbidNotifications(auctionOpenedWindowStart);
        } catch (RuntimeException exception) {
            log.error("event=notification.recovery.urgent.failed now={}", now, exception);
            throw exception;
        }
    }
}
