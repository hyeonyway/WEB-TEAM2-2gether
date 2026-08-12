package com.dbidding.notification.recovery.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

class UrgentNotificationRecoverySchedulerTest {
    private final NotificationReconciliationService notificationReconciliationService =
            mock(NotificationReconciliationService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final UrgentNotificationRecoveryScheduler scheduler =
            new UrgentNotificationRecoveryScheduler(notificationReconciliationService, clock);

    @Test
    void 스케줄러는_10분_window로_ENDING_경매의_상회입찰_복구만_수행한다() {
        scheduler.recover();

        verify(notificationReconciliationService)
                .recoverOutbidNotifications(Instant.parse("2026-07-29T00:50:00Z"), List.of(AuctionStatus.ENDING));
        verify(notificationReconciliationService, never()).recoverAuctionOpenedNotifications(any());
    }
}
