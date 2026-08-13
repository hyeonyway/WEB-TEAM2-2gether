package com.dbidding.auction.service;

import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private static final Duration ENDING_WINDOW = Duration.ofMinutes(5);

    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor;
    private final AuctionRepository auctionRepository;
    private final Optional<AuctionEndingTransitionService> auctionEndingTransitionService;
    private final Clock clock;

    public AuctionClosingScheduler(
            AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor,
            AuctionRepository auctionRepository,
            @Autowired(required = false) Optional<AuctionEndingTransitionService> auctionEndingTransitionService,
            Clock clock
    ) {
        this.auctionCloseSchedulerProcessor = auctionCloseSchedulerProcessor;
        this.auctionRepository = auctionRepository;
        this.auctionEndingTransitionService = auctionEndingTransitionService == null
                ? Optional.empty() : auctionEndingTransitionService;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${auction.closing.scheduler.fixed-delay-ms:60000}",
            scheduler = "auctionBackupTaskScheduler"
    )
    public void closeDueAuctions() {
        Instant now = clock.instant();
        log.debug("event=auction.close.backup_scheduler.started now={} batchSize={}", now, CLOSE_BATCH_SIZE);
        try {
            transitionOverdueEndingAuctions(now);
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

    private void transitionOverdueEndingAuctions(Instant now) {
        if (auctionEndingTransitionService.isEmpty()) {
            return;
        }
        List<Integer> auctionIds = auctionRepository.findOverdueEndingCandidateIds(
                now.plus(ENDING_WINDOW), PageRequest.of(0, CLOSE_BATCH_SIZE)
        );
        if (auctionIds.isEmpty()) {
            log.debug("event=auction.ending.backup_scheduler.empty now={}", now);
            return;
        }
        auctionIds.forEach(auctionId -> auctionEndingTransitionService.get().transitionIfDue(auctionId, now));
        log.info("event=auction.ending.backup_scheduler.completed count={} auctionIds={}", auctionIds.size(), auctionIds);
    }
}
