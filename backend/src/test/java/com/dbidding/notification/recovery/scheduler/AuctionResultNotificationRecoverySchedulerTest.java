package com.dbidding.notification.recovery.scheduler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.notification.recovery.NotificationReconciliationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
    void 스케줄러는_경매_생성과_OPEN_상회입찰은_15분_window로_경매_종료는_20분_window로_복구한다() {
        scheduler.recover();

        verify(notificationReconciliationService)
                .recoverAuctionOpenedNotifications(Instant.parse("2026-07-29T00:45:00Z"));
        verify(notificationReconciliationService)
                .recoverAuctionClosedNotifications(Instant.parse("2026-07-29T00:40:00Z"));
        verify(notificationReconciliationService)
                .recoverOutbidNotifications(Instant.parse("2026-07-29T00:45:00Z"), List.of(AuctionStatus.OPEN));
    }
}
