package com.dbidding.auction.service.dblock;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionEndingPolicy;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code AuctionDeadlineScheduler}의 DB row-lock 경로. 다음 종료 대상을
 * {@link AuctionRepository}에서 조회한다 — Redis를 전혀 쓰지 않는다.
 */
@Slf4j
@Component
@Profile("!redis")
@ConditionalOnProperty(
        name = "auction.deadline.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DbAuctionDeadlineScheduler {
    private static final int CLOSE_BATCH_SIZE = 100;

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor;
    private final AuctionRepository auctionRepository;
    private final AuctionEndingTransitionProcessor auctionEndingTransitionProcessor;
    private final TaskScheduler taskScheduler;
    private final java.time.Clock clock;
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> scheduledTask;
    private Integer scheduledAuctionId;
    private Instant scheduledCloseTime;

    public DbAuctionDeadlineScheduler(
            AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor,
            AuctionRepository auctionRepository,
            AuctionEndingTransitionProcessor auctionEndingTransitionProcessor,
            @Qualifier("auctionDeadlineTaskScheduler") TaskScheduler taskScheduler,
            java.time.Clock clock
    ) {
        this.auctionCloseSchedulerProcessor = auctionCloseSchedulerProcessor;
        this.auctionRepository = auctionRepository;
        this.auctionEndingTransitionProcessor = auctionEndingTransitionProcessor;
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
        List<Auction> openCandidates = auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.OPEN), PageRequest.of(0, 1)
        );
        List<Auction> endingCandidates = auctionRepository.findNextCloseTarget(
                List.of(AuctionStatus.ENDING), PageRequest.of(0, 1)
        );
        ScheduledAuctionTarget openTarget = openCandidates.isEmpty() ? null
                : new ScheduledAuctionTarget(
                        openCandidates.get(0).getId(),
                        openCandidates.get(0).getCloseTime().minus(AuctionEndingPolicy.WINDOW)
                );
        ScheduledAuctionTarget endingTarget = endingCandidates.isEmpty() ? null
                : new ScheduledAuctionTarget(endingCandidates.get(0).getId(), endingCandidates.get(0).getCloseTime());
        if (openTarget == null) {
            return endingTarget;
        }
        if (endingTarget == null) {
            return openTarget;
        }
        return openTarget.closeTime().isBefore(endingTarget.closeTime()) ? openTarget : endingTarget;
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
