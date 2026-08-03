package com.dbidding.auction.service;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "auction.deadline.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AuctionDeadlineScheduler {
    private static final int CLOSE_BATCH_SIZE = 100;

    private final AuctionService auctionService;
    private final AuctionRepository auctionRepository;
    private final TaskScheduler taskScheduler;
    private final Clock clock;
    private final Object scheduleLock = new Object();
    private ScheduledFuture<?> scheduledTask;
    private Integer scheduledAuctionId;
    private LocalDateTime scheduledCloseTime;

    public AuctionDeadlineScheduler(
            AuctionService auctionService,
            AuctionRepository auctionRepository,
            @Qualifier("auctionDeadlineTaskScheduler") TaskScheduler taskScheduler,
            Clock clock
    ) {
        this.auctionService = auctionService;
        this.auctionRepository = auctionRepository;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleOnStartup() {
        scheduleNext("application_ready");
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
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
            List<Auction> nextTargets = auctionRepository.findNextCloseTarget(
                    List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                    PageRequest.of(0, 1)
            );
            if (nextTargets.isEmpty()) {
                cancelScheduledTask();
                scheduledAuctionId = null;
                scheduledCloseTime = null;
                log.info("event=auction.close.deadline.unscheduled reason={} target=none", reason);
                return;
            }

            Auction nextTarget = nextTargets.get(0);
            cancelScheduledTask();
            scheduledAuctionId = nextTarget.getId();
            scheduledCloseTime = nextTarget.getCloseTime();
            scheduledTask = taskScheduler.schedule(
                    this::closeDueAuctionsAtDeadline,
                    scheduledCloseTime.atZone(zone()).toInstant()
            );
            log.info(
                    "event=auction.close.deadline.scheduled auctionId={} closeTime={} reason={}",
                    scheduledAuctionId,
                    scheduledCloseTime,
                    reason
            );
        }
    }

    private void closeDueAuctionsAtDeadline() {
        LocalDateTime now = LocalDateTime.now(clock);
        log.info(
                "event=auction.close.deadline.triggered scheduledAuctionId={} scheduledCloseTime={} now={} batchSize={}",
                scheduledAuctionId,
                scheduledCloseTime,
                now,
                CLOSE_BATCH_SIZE
        );
        try {
            var closedAuctions = auctionService.closeDueAuctions(now, CLOSE_BATCH_SIZE);
            log.info(
                    "event=auction.close.deadline.completed closedCount={} auctionIds={}",
                    closedAuctions.size(),
                    closedAuctions.stream().map(response -> response.auctionId()).toList()
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

    private ZoneId zone() {
        return clock.getZone();
    }
}
