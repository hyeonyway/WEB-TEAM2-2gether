package com.dbidding.notification.recovery.scheduler;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 마감 임박(ENDING) 경매의 상회 입찰 알림 유실 복구. 앤티스나이핑 자동 연장(5분) 안에
 * 복구해야 재입찰 기회가 보장되는 유일한 대상이라 AuctionResultNotificationRecoveryScheduler
 * 보다 짧은 주기로 돈다. 경매 생성 복구와 OPEN 경매의 상회 입찰 복구는 그만큼 급하지 않아
 * 비긴급 스케줄러로 옮겼다(이슈 #373).
 * 설계 근거: docs/hamin/notification/6-notification-recovery-batch.md 결정 5,
 * docs/hamin/notification/18-recovery-scheduler-urgency-split-plan.md
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

    // 1분 30초에 한번 -> 10분 윈도우(종료 경계 캐치용)
    @Scheduled(
            fixedDelayString = "${notification.recovery.urgent.fixed-delay-ms:90000}",
            scheduler = "notificationRecoveryTaskScheduler"
    )
    public void recover() {
        Instant now = clock.instant();
        Instant closedAuctionCatchWindowStart = now.minus(Duration.ofMinutes(10));
        try {
            notificationReconciliationService.recoverOutbidNotifications(
                    closedAuctionCatchWindowStart, List.of(AuctionStatus.ENDING)
            );
        } catch (RuntimeException exception) {
            log.error("event=notification.recovery.urgent.failed now={}", now, exception);
            throw exception;
        }
    }
}
