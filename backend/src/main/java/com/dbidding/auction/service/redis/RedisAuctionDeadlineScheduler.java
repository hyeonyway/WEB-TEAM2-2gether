package com.dbidding.auction.service.redis;

import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code AuctionDeadlineScheduler}의 Redis 경로. 다음 종료 대상을 Redis 활성/ending-window
 * 인덱스(ZSET)에서 찾는다 — MySQL을 조회하지 않는다.
 */
@Slf4j
@Component
@Profile("redis")
@ConditionalOnProperty(
        name = "auction.deadline.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RedisAuctionDeadlineScheduler {
    private static final int CLOSE_BATCH_SIZE = 100;
    private static final String ACTIVE_BY_CLOSE_TIME = "auction:active:by-close-time";
    private static final String ENDING_WINDOW_BY_CLOSE_TIME = "auction:ending-window:by-close-time";

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor;
    private final AuctionEndingTransitionProcessor auctionEndingTransitionProcessor;
    private final StringRedisTemplate redisTemplate;
    private final TaskScheduler taskScheduler;
    private final java.time.Clock clock;
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> scheduledTask;
    private Integer scheduledAuctionId;
    private Instant scheduledCloseTime;

    public RedisAuctionDeadlineScheduler(
            AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor,
            AuctionEndingTransitionProcessor auctionEndingTransitionProcessor,
            StringRedisTemplate redisTemplate,
            @Qualifier("auctionDeadlineTaskScheduler") TaskScheduler taskScheduler,
            java.time.Clock clock
    ) {
        this.auctionCloseSchedulerProcessor = auctionCloseSchedulerProcessor;
        this.auctionEndingTransitionProcessor = auctionEndingTransitionProcessor;
        this.redisTemplate = redisTemplate;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleOnStartup() {
        scheduleNext("application_ready");
    }

    // fallbackExecution=true: #281 이후 입찰 경로에서 이 이벤트가 트랜잭션 밖(이미 커밋된 뒤)에서도
    // 발행되므로, 없으면 활성 트랜잭션이 없을 때 조용히 드랍된다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void reschedule(AuctionCloseScheduleChangedEvent event) {
        log.debug(
                "event=auction.close.deadline.reschedule_requested auctionId={} closeTime={} reason={}",
                event.auctionId(),
                event.closeTime(),
                event.reason()
        );
        scheduleNext(event.reason());
    }

    void scheduleNext(String reason) {
        synchronized (scheduleLock) {
            ScheduledAuctionTarget nextTarget = nextTarget();
            if (nextTarget == null) {
                cancelScheduledTask();
                scheduledAuctionId = null;
                scheduledCloseTime = null;
                log.info("event=auction.close.deadline.unscheduled reason={} target=none", reason);
                return;
            }

            cancelScheduledTask();
            scheduledAuctionId = nextTarget.auctionId();
            scheduledCloseTime = nextTarget.closeTime();
            scheduledTask = taskScheduler.schedule(
                    this::closeDueAuctionsAtDeadline,
                    scheduledCloseTime
            );
            log.info(
                    "event=auction.close.deadline.scheduled auctionId={} closeTime={} reason={}",
                    scheduledAuctionId,
                    scheduledCloseTime,
                    reason
            );
        }
    }

    private ScheduledAuctionTarget nextTarget() {
        ScheduledAuctionTarget activeTarget = redisTarget(ACTIVE_BY_CLOSE_TIME);
        ScheduledAuctionTarget endingTarget = redisTarget(ENDING_WINDOW_BY_CLOSE_TIME);
        if (activeTarget == null) return endingTarget;
        if (endingTarget == null) return activeTarget;
        return activeTarget.closeTime().isBefore(endingTarget.closeTime()) ? activeTarget : endingTarget;
    }

    private ScheduledAuctionTarget redisTarget(String key) {
        Set<ZSetOperations.TypedTuple<String>> targets = redisTemplate.opsForZSet().rangeWithScores(key, 0, 0);
        if (targets == null || targets.isEmpty()) return null;
        ZSetOperations.TypedTuple<String> target = targets.iterator().next();
        if (target.getValue() == null || target.getScore() == null) return null;
        return new ScheduledAuctionTarget(Integer.valueOf(target.getValue()), Instant.ofEpochMilli(target.getScore().longValue()));
    }

    private record ScheduledAuctionTarget(Integer auctionId, Instant closeTime) {
    }

    private void closeDueAuctionsAtDeadline() {
        Instant now = clock.instant();
        Integer firedAuctionId = scheduledAuctionId;
        log.info(
                "event=auction.close.deadline.triggered scheduledAuctionId={} scheduledCloseTime={} now={} batchSize={}",
                scheduledAuctionId,
                scheduledCloseTime,
                now,
                CLOSE_BATCH_SIZE
        );
        try {
            var transitionedAuctions = auctionEndingTransitionProcessor.transitionDueAuctions(now, CLOSE_BATCH_SIZE);
            log.info("event=auction.ending.deadline.completed transitionedCount={} auctionIds={}",
                    transitionedAuctions.size(), transitionedAuctions);
            var closedAuctions = auctionCloseSchedulerProcessor.processDueAuctions(now, CLOSE_BATCH_SIZE);
            log.info(
                    "event=auction.close.deadline.completed closedCount={} auctionIds={}",
                    closedAuctions.size(),
                    closedAuctions
            );
        } catch (RuntimeException exception) {
            log.error(
                    "event=auction.close.deadline.failed scheduledAuctionId={} scheduledCloseTime={} now={} batchSize={}",
                    scheduledAuctionId,
                    scheduledCloseTime,
                    now,
                    CLOSE_BATCH_SIZE,
                    exception
            );
            throw exception;
        } finally {
            scheduleNext("deadline_executed");
        }
    }

    private void cancelScheduledTask() {
        if (scheduledTask != null && !scheduledTask.isDone()) {
            scheduledTask.cancel(false);
        }
    }
}
