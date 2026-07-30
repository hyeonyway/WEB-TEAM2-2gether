package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
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
    private final AuctionService auctionService = mock(AuctionService.class);
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final AuctionDeadlineScheduler scheduler = new AuctionDeadlineScheduler(
            auctionService,
            auctionRepository,
            taskScheduler,
            clock
    );

    @Test
    void 가장_가까운_마감_시간에_맞춰_종료_작업을_예약한다() {
        Auction auction = auction(1, LocalDateTime.of(2026, 7, 29, 10, 5));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(auction));

        scheduler.scheduleNext("test");

        assertThat(taskScheduler.scheduledInstant)
                .isEqualTo(LocalDateTime.of(2026, 7, 29, 10, 5).atZone(clock.getZone()).toInstant());
    }

    @Test
    void 예약된_작업이_실행되면_현재_시간_기준으로_종료_대상을_닫고_다음_마감을_다시_예약한다() {
        Auction auction = auction(1, LocalDateTime.of(2026, 7, 29, 10, 0));
        when(auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                PageRequest.of(0, 1)
        )).thenReturn(List.of(auction), List.of());
        when(auctionService.closeDueAuctions(LocalDateTime.of(2026, 7, 29, 10, 0), 100))
                .thenReturn(List.of());

        scheduler.scheduleNext("test");
        taskScheduler.scheduledTask.run();

        verify(auctionService).closeDueAuctions(LocalDateTime.of(2026, 7, 29, 10, 0), 100);
    }

    private Auction auction(Integer id, LocalDateTime closeTime) {
        Auction auction = Auction.builder()
                .sellerId(1)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minusHours(1))
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

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            this.scheduledTask = task;
            this.scheduledInstant = startTime;
            return new CompletedScheduledFuture();
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
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
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
