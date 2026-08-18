package com.dbidding.auction.service.dblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.test.util.ReflectionTestUtils;

class DbAuctionDeadlineSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-29T01:00:00Z");

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final AuctionEndingTransitionProcessor auctionEndingTransitionProcessor = mock(AuctionEndingTransitionProcessor.class);
    private final CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    private final DbAuctionDeadlineScheduler scheduler = new DbAuctionDeadlineScheduler(
            auctionCloseSchedulerProcessor,
            auctionRepository,
            auctionEndingTransitionProcessor,
            taskScheduler,
            clock
    );

    @Test
    void OPEN_경매는_ENDING_진입시각에_맞춰_예약한다() {
        Auction auction = auction(1, AuctionStatus.OPEN, NOW.plus(Duration.ofMinutes(10)));
        stubCandidates(auction, null);

        scheduler.scheduleNext("test");

        assertThat(taskScheduler.scheduledInstant).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    }

    @Test
    void ENDING_경매는_실제_마감시각에_맞춰_예약한다() {
        Auction auction = auction(1, AuctionStatus.ENDING, NOW.plus(Duration.ofMinutes(5)));
        stubCandidates(null, auction);

        scheduler.scheduleNext("test");

        assertThat(taskScheduler.scheduledInstant).isEqualTo(auction.getCloseTime());
    }

    @Test
    void OPEN_전환시각과_ENDING_마감시각중_더_이른_대상을_예약한다() {
        Auction open = auction(1, AuctionStatus.OPEN, NOW.plus(Duration.ofMinutes(12)));
        Auction ending = auction(2, AuctionStatus.ENDING, NOW.plus(Duration.ofMinutes(6)));
        stubCandidates(open, ending);

        scheduler.scheduleNext("test");

        assertThat(taskScheduler.scheduledInstant).isEqualTo(ending.getCloseTime());
    }

    @Test
    void 타이머가_발동하면_ENDING_전이후_마감처리를_수행한다() {
        Auction open = auction(1, AuctionStatus.OPEN, NOW.plus(Duration.ofMinutes(5)));
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1)))
                .thenReturn(List.of(open), List.of());
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
                .thenReturn(List.of());
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenReturn(List.of());

        scheduler.scheduleNext("test");
        taskScheduler.scheduledTask.run();

        var order = org.mockito.Mockito.inOrder(auctionEndingTransitionProcessor, auctionCloseSchedulerProcessor);
        order.verify(auctionEndingTransitionProcessor).transitionDueAuctions(NOW, 100);
        order.verify(auctionCloseSchedulerProcessor).processDueAuctions(NOW, 100);
    }

    @Test
    void 일정_변경_이벤트를_받으면_기존_예약을_취소하고_다시_계산한다() {
        Auction first = auction(1, AuctionStatus.OPEN, NOW.plus(Duration.ofMinutes(10)));
        Auction changed = auction(2, AuctionStatus.OPEN, NOW.plus(Duration.ofMinutes(7)));
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1)))
                .thenReturn(List.of(first), List.of(changed));
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
                .thenReturn(List.of());

        scheduler.scheduleNext("initial");
        CompletedScheduledFuture firstFuture = taskScheduler.scheduledFuture;
        scheduler.reschedule(new AuctionCloseScheduleChangedEvent(changed.getId(), changed.getCloseTime(), "ending_transition"));

        assertThat(firstFuture.cancelled).isTrue();
        assertThat(taskScheduler.scheduledInstant).isEqualTo(changed.getCloseTime().minus(Duration.ofMinutes(5)));
    }

    @Test
    void 타이머_처리가_실패해도_다음_대상을_다시_예약한다() {
        Auction failed = auction(1, AuctionStatus.ENDING, NOW);
        Auction next = auction(2, AuctionStatus.ENDING, NOW.plus(Duration.ofMinutes(5)));
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1))).thenReturn(List.of());
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
                .thenReturn(List.of(failed), List.of(next));
        when(auctionCloseSchedulerProcessor.processDueAuctions(NOW, 100)).thenThrow(new IllegalStateException("close failed"));

        scheduler.scheduleNext("initial");

        assertThatThrownBy(taskScheduler.scheduledTask::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("close failed");
        verify(auctionRepository, times(2)).findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1));
        assertThat(taskScheduler.scheduledInstant).isEqualTo(next.getCloseTime());
    }

    @Test
    void 다음_대상이_없으면_기존_예약을_취소한다() {
        Auction auction = auction(1, AuctionStatus.OPEN, NOW.plus(Duration.ofMinutes(10)));
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1)))
                .thenReturn(List.of(auction), List.of());
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
                .thenReturn(List.of());

        scheduler.scheduleNext("initial");
        CompletedScheduledFuture scheduledFuture = taskScheduler.scheduledFuture;
        scheduler.scheduleNext("empty");

        assertThat(scheduledFuture.cancelled).isTrue();
        assertThat(taskScheduler.scheduledTask).isNotNull();
    }

    private void stubCandidates(Auction open, Auction ending) {
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1)))
                .thenReturn(open == null ? List.of() : List.of(open));
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)))
                .thenReturn(ending == null ? List.of() : List.of(ending));
    }

    private Auction auction(Integer id, AuctionStatus status, Instant closeTime) {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(1).auctionName("경매 A").description("카드 상태 설명")
                .startPrice(42_000L).buyNowPrice(100_000L).deliveryFee(3_000L)
                .openTime(closeTime.minus(Duration.ofHours(1)))
                .estimatedCloseTime(closeTime).closeTime(closeTime)
                .bidPriceUnit(1_000L).hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        ReflectionTestUtils.setField(auction, "status", status);
        return auction;
    }

    private static class CapturingTaskScheduler implements TaskScheduler {
        private Runnable scheduledTask;
        private Instant scheduledInstant;
        private CompletedScheduledFuture scheduledFuture;

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            scheduledTask = task;
            scheduledInstant = startTime;
            scheduledFuture = new CompletedScheduledFuture();
            return scheduledFuture;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
            throw new UnsupportedOperationException();
        }
    }

    private static class CompletedScheduledFuture implements ScheduledFuture<Object> {
        private boolean cancelled;

        @Override public long getDelay(TimeUnit unit) { return 0; }
        @Override public int compareTo(Delayed other) { return 0; }
        @Override public boolean cancel(boolean mayInterruptIfRunning) { cancelled = true; return true; }
        @Override public boolean isCancelled() { return cancelled; }
        @Override public boolean isDone() { return false; }
        @Override public Object get() { return null; }
        @Override public Object get(long timeout, TimeUnit unit) { return null; }
    }
}
