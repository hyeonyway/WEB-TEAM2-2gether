package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;

class AuctionClosingSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-08-12T10:00:00Z");

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final AuctionEndingTransitionService auctionEndingTransitionService = mock(AuctionEndingTransitionService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    private final AuctionClosingScheduler scheduler = new AuctionClosingScheduler(
            auctionCloseSchedulerProcessor,
            auctionRepository,
            Optional.of(auctionEndingTransitionService),
            clock
    );

    @Test
    void 백업_스케줄러는_ENDING_진입_후보를_처리한_뒤_실제_마감_처리를_수행한다() {
        when(auctionRepository.findDueAuctionIds(
                List.of(com.dbidding.auction.domain.AuctionStatus.OPEN), NOW.plus(Duration.ofMinutes(5)), PageRequest.of(0, 100)
        )).thenReturn(List.of(1, 2));
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenReturn(List.of());

        scheduler.closeDueAuctions();

        var order = inOrder(auctionEndingTransitionService, auctionCloseSchedulerProcessor);
        order.verify(auctionEndingTransitionService).transitionIfDue(1, NOW);
        order.verify(auctionEndingTransitionService).transitionIfDue(2, NOW);
        order.verify(auctionCloseSchedulerProcessor).processDueAuctions(NOW, 100);
    }

    @Test
    void Redis_프로필처럼_전환_서비스가_없어도_기존_마감처리는_수행한다() {
        AuctionClosingScheduler redisScheduler = new AuctionClosingScheduler(
                auctionCloseSchedulerProcessor, auctionRepository, Optional.empty(), clock
        );
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenReturn(List.of());

        redisScheduler.closeDueAuctions();

        verify(auctionRepository, never()).findDueAuctionIds(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(auctionCloseSchedulerProcessor).processDueAuctions(NOW, 100);
    }

    @Test
    void 전환_후보_하나가_실패해도_같은_틱의_실제_마감처리는_수행한다() {
        when(auctionRepository.findDueAuctionIds(
                List.of(com.dbidding.auction.domain.AuctionStatus.OPEN), NOW.plus(Duration.ofMinutes(5)), PageRequest.of(0, 100)
        )).thenReturn(List.of(1, 2));
        doThrow(new IllegalStateException("lock failed")).when(auctionEndingTransitionService).transitionIfDue(1, NOW);
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenReturn(List.of());

        scheduler.closeDueAuctions();

        verify(auctionEndingTransitionService).transitionIfDue(2, NOW);
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
