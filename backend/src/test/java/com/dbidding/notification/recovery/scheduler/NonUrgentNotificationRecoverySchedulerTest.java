package com.dbidding.notification.recovery.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class NonUrgentNotificationRecoverySchedulerTest {
    private final NotificationReconciliationService notificationReconciliationService =
            mock(NotificationReconciliationService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final NonUrgentNotificationRecoveryScheduler scheduler =
            new NonUrgentNotificationRecoveryScheduler(notificationReconciliationService, clock);

    @Test
    void 스케줄러는_경매_생성과_OPEN_상회입찰은_15분_window로_경매_종료는_20분_window로_복구한다() {
        scheduler.recover();

        verify(notificationReconciliationService)
                .recoverAuctionOpenedNotifications(Instant.parse("2026-07-29T00:45:00Z"));
        verify(notificationReconciliationService)
                .recoverAuctionClosedNotifications(Instant.parse("2026-07-29T00:40:00Z"));
        verify(notificationReconciliationService)
                .recoverOpenOutbidNotifications(Instant.parse("2026-07-29T00:45:00Z"));
    }
}
