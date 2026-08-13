package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

class AuctionClosingSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
    private final AuctionEndingTransitionProcessor auctionEndingTransitionProcessor = mock(AuctionEndingTransitionProcessor.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    private final AuctionClosingScheduler scheduler = new AuctionClosingScheduler(
            auctionCloseSchedulerProcessor,
            auctionEndingTransitionProcessor,
            clock
    );

    @Test
    void 백업_스케줄러는_ENDING_진입_후보를_처리한_뒤_실제_마감_처리를_수행한다() {
        when(auctionEndingTransitionProcessor.transitionDueAuctions(NOW, 100)).thenReturn(List.of(1, 2));
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenReturn(List.of());

        scheduler.closeDueAuctions();

        var order = inOrder(auctionEndingTransitionProcessor, auctionCloseSchedulerProcessor);
        order.verify(auctionEndingTransitionProcessor).transitionDueAuctions(NOW, 100);
        order.verify(auctionCloseSchedulerProcessor).processDueAuctions(NOW, 100);
    }

    @Test
    void 전이_대상이_없어도_기존_마감처리는_수행한다() {
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenReturn(List.of());

        scheduler.closeDueAuctions();

        verify(auctionEndingTransitionProcessor).transitionDueAuctions(NOW, 100);
        verify(auctionCloseSchedulerProcessor).processDueAuctions(NOW, 100);
    }

    @Test
    void 백업_스케줄러의_기본_주기는_60초다() throws NoSuchMethodException {
        Scheduled scheduled = AuctionClosingScheduler.class
                .getDeclaredMethod("closeDueAuctions")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${auction.closing.scheduler.fixed-delay-ms:60000}");
    }
}
