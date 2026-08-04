package com.dbidding.batch.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.batch.service.NotificationReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AuctionResultNotificationRecoverySchedulerTest {
    private final NotificationReconciliationService notificationReconciliationService =
            mock(NotificationReconciliationService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final AuctionResultNotificationRecoveryScheduler scheduler =
            new AuctionResultNotificationRecoveryScheduler(notificationReconciliationService, clock);

    @Test
    void 스케줄러는_20분_window로_경매_종료_복구를_수행한다() {
        scheduler.recover();

        verify(notificationReconciliationService)
                .recoverAuctionClosedNotifications(LocalDateTime.of(2026, 7, 29, 9, 40));
    }
}
