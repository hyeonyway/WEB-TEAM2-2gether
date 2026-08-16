package com.dbidding.auction.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(
        name = "auction.closing.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AuctionClosingScheduler {
    private static final int CLOSE_BATCH_SIZE = 100;

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor;
    private final AuctionEndingTransitionProcessor auctionEndingTransitionProcessor;
    private final Clock clock;

    public AuctionClosingScheduler(
            AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor,
            AuctionEndingTransitionProcessor auctionEndingTransitionProcessor,
            Clock clock
    ) {
        this.auctionCloseSchedulerProcessor = auctionCloseSchedulerProcessor;
        this.auctionEndingTransitionProcessor = auctionEndingTransitionProcessor;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${auction.closing.scheduler.fixed-delay-ms:60000}",
            scheduler = "auctionBackupTaskScheduler"
    )
    @SchedulerLock(
            name = "auction-closing-backup-scheduler",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT5M"
    )
    public void closeDueAuctions() {
        Instant now = clock.instant();
        log.debug("event=auction.close.backup_scheduler.started now={} batchSize={}", now, CLOSE_BATCH_SIZE);
        try {
            List<Integer> transitionedAuctionIds = auctionEndingTransitionProcessor.transitionDueAuctions(now, CLOSE_BATCH_SIZE);
            if (!transitionedAuctionIds.isEmpty()) {
                log.info("event=auction.ending.backup_scheduler.completed count={} auctionIds={}", transitionedAuctionIds.size(), transitionedAuctionIds);
            }
            var auctionIds = auctionCloseSchedulerProcessor.processDueAuctions(now, CLOSE_BATCH_SIZE);
            if (auctionIds.isEmpty()) {
                log.debug("event=auction.close.backup_scheduler.empty now={}", now);
                return;
            }
            log.info(
                    "event=auction.close.backup_scheduler.completed closedCount={} auctionIds={}",
                    auctionIds.size(),
                    auctionIds
            );
        } catch (RuntimeException exception) {
            log.error("event=auction.close.backup_scheduler.failed now={} batchSize={}", now, CLOSE_BATCH_SIZE, exception);
            throw exception;
        }
    }

}
