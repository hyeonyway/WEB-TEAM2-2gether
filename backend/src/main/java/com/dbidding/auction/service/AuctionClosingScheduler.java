package com.dbidding.auction.service;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "auction.closing.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class AuctionClosingScheduler {
    private static final int CLOSE_BATCH_SIZE = 100;

    private final AuctionCommandService auctionCommandService;
    private final Clock clock;

    @Scheduled(
            fixedDelayString = "${auction.closing.scheduler.fixed-delay-ms:10000}",
            scheduler = "auctionBackupTaskScheduler"
    )
    public void closeDueAuctions() {
        Instant now = clock.instant();
        log.debug("event=auction.close.backup_scheduler.started now={} batchSize={}", now, CLOSE_BATCH_SIZE);
        try {
            var closedAuctions = auctionCommandService.closeDueAuctions(now, CLOSE_BATCH_SIZE);
            if (closedAuctions.isEmpty()) {
                log.debug("event=auction.close.backup_scheduler.empty now={}", now);
                return;
            }
            log.info(
                    "event=auction.close.backup_scheduler.completed closedCount={} auctionIds={}",
                    closedAuctions.size(),
                    closedAuctions.stream().map(response -> response.auctionId()).toList()
            );
        } catch (RuntimeException exception) {
            log.error("event=auction.close.backup_scheduler.failed now={} batchSize={}", now, CLOSE_BATCH_SIZE, exception);
            throw exception;
        }
    }
}
