package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
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

class AuctionDeadlineSchedulerTest {
    private final AuctionDueClosingService auctionDueClosingService = mock(AuctionDueClosingService.class);
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final AuctionDeadlineScheduler scheduler = new AuctionDeadlineScheduler(
            auctionDueClosingService,
            auctionRepository,
            taskScheduler,
            clock
    );

    @Test
    void 가장_가까운_마감_시간에_맞춰_종료_작업을_예약한다() {
        Auction auction = auction(1, Instant.parse("2026-07-29T01:05:00Z"));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(auction));

        scheduler.scheduleNext("test");

        assertThat(taskScheduler.scheduledInstant)
                .isEqualTo(Instant.parse("2026-07-29T01:05:00Z"));
    }

    @Test
    void 예약된_작업이_실행되면_현재_시간_기준으로_종료_대상을_닫고_다음_마감을_다시_예약한다() {
        Auction auction = auction(1, Instant.parse("2026-07-29T01:00:00Z"));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(auction), List.of());
        when(auctionDueClosingService.closeDueAuctions(Instant.parse("2026-07-29T01:00:00Z"), 100))
                .thenReturn(List.of());

        scheduler.scheduleNext("test");
        taskScheduler.scheduledTask.run();

        verify(auctionDueClosingService).closeDueAuctions(Instant.parse("2026-07-29T01:00:00Z"), 100);
    }

    @Test
    void 마감_일정_변경_이벤트를_받으면_기존_작업을_취소하고_가장_빠른_마감을_다시_예약한다() {
        Auction first = auction(1, Instant.parse("2026-07-29T01:10:00Z"));
        Auction changed = auction(2, Instant.parse("2026-07-29T01:05:00Z"));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(first), List.of(changed));

        scheduler.scheduleNext("initial");
        CompletedScheduledFuture firstFuture = taskScheduler.scheduledFuture;
        scheduler.reschedule(new AuctionCloseScheduleChangedEvent(
                changed.getId(),
                changed.getCloseTime(),
                "auction_created"
        ));

        assertThat(firstFuture.cancelled).isTrue();
        assertThat(taskScheduler.scheduledInstant)
                .isEqualTo(changed.getCloseTime());
    }

    @Test
    void 정시_마감이_실패해도_다음_마감_대상을_예약한다() {
        Auction failedTarget = auction(1, Instant.parse("2026-07-29T01:00:00Z"));
        Auction nextTarget = auction(2, Instant.parse("2026-07-29T01:05:00Z"));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(failedTarget), List.of(nextTarget));
        when(auctionDueClosingService.closeDueAuctions(Instant.parse("2026-07-29T01:00:00Z"), 100))
                .thenThrow(new IllegalStateException("close failed"));

        scheduler.scheduleNext("initial");

        assertThatThrownBy(taskScheduler.scheduledTask::run)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("close failed");
        verify(auctionRepository, times(2)).findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        );
        assertThat(taskScheduler.scheduledInstant)
                .isEqualTo(nextTarget.getCloseTime());
    }

    @Test
    void 다음_마감_대상이_없으면_기존_예약을_취소한다() {
        Auction auction = auction(1, Instant.parse("2026-07-29T01:05:00Z"));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(auction), List.of());

        scheduler.scheduleNext("initial");
        CompletedScheduledFuture scheduledFuture = taskScheduler.scheduledFuture;
        scheduler.scheduleNext("empty");

        assertThat(scheduledFuture.cancelled).isTrue();
    }

    private Auction auction(Integer id, Instant closeTime) {
        Auction auction = Auction.builder()
                .sellerId(1)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minus(Duration.ofHours(1)))
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
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
            this.scheduledTask = task;
            this.scheduledInstant = startTime;
            this.scheduledFuture = new CompletedScheduledFuture();
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

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
