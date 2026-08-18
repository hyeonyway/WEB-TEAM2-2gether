package com.dbidding.auction.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;

class RedisAuctionDeadlineSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-07-29T01:00:00Z");

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
    private final AuctionEndingTransitionProcessor auctionEndingTransitionProcessor = mock(AuctionEndingTransitionProcessor.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final CapturingTaskScheduler taskScheduler = new CapturingTaskScheduler();
    private final Clock clock = Clock.fixed(NOW, ZoneId.of("Asia/Seoul"));
    private final RedisAuctionDeadlineScheduler scheduler = new RedisAuctionDeadlineScheduler(
            auctionCloseSchedulerProcessor,
            auctionEndingTransitionProcessor,
            redisTemplate,
            taskScheduler,
            clock
    );

    @Test
    void Redis_활성_경매_인덱스의_가장_빠른_마감_시간에_종료_작업을_예약한다() {
        AuctionRepository auctionRepository = mock(AuctionRepository.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        Instant closeTime = Instant.parse("2026-07-29T01:05:00Z");
        when(zSetOperations.rangeWithScores("auction:active:by-close-time", 0, 0))
                .thenReturn(new java.util.LinkedHashSet<>(java.util.List.of(
                        new DefaultTypedTuple<>("7", (double) closeTime.toEpochMilli())
                )));

        scheduler.scheduleNext("redis_test");

        assertThat(taskScheduler.scheduledInstant).isEqualTo(closeTime);
        verify(auctionRepository, never()).findNextCloseTarget(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static class CapturingTaskScheduler implements TaskScheduler {
        private Runnable scheduledTask;
        private Instant scheduledInstant;
        private ScheduledFuture<?> scheduledFuture;

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
