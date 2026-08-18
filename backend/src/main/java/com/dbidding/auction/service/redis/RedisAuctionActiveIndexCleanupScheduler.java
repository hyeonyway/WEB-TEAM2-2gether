package com.dbidding.auction.service.redis;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 종료된 경매가 비정상 경로로 활성 ZSET에 남은 경우만 보수적으로 제거한다. */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
class RedisAuctionActiveIndexCleanupScheduler {
    private static final String ACTIVE_BY_CLOSE_TIME = "auction:active:by-close-time";
    private static final String ENDING_WINDOW_BY_CLOSE_TIME = "auction:ending-window:by-close-time";
    private static final List<String> ACTIVE_INDEX_KEYS = List.of(
            ACTIVE_BY_CLOSE_TIME, "auction:active:by-bid-count", "auction:active:by-price",
            "auction:active:by-change-rate", "auction:active:by-open-time"
    );

    private final StringRedisTemplate redisTemplate;
    @Qualifier("auctionActiveIndexGcScript") private final RedisScript<Long> auctionActiveIndexGcScript;
    @Qualifier("auctionEndingWindowIndexGcScript") private final RedisScript<Long> auctionEndingWindowIndexGcScript;
    private final Clock clock;
    @Value("${auction.active-index.cleanup.stale-after:PT24H}") private Duration staleAfter;
    @Value("${auction.active-index.cleanup.batch-size:100}") private int batchSize;

    @Scheduled(fixedDelayString = "${auction.active-index.cleanup.fixed-delay-ms:3600000}")
    @SchedulerLock(
            name = "auction-active-index-cleanup",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT10M"
    )
    void removeTerminalEntries() {
        Instant threshold = clock.instant().minus(staleAfter);
        Long removed = redisTemplate.execute(auctionActiveIndexGcScript, ACTIVE_INDEX_KEYS,
                String.valueOf(threshold.toEpochMilli()), String.valueOf(batchSize));
        Long endingWindowRemoved = redisTemplate.execute(auctionEndingWindowIndexGcScript, List.of(ENDING_WINDOW_BY_CLOSE_TIME),
                String.valueOf(batchSize));
        if ((removed != null && removed > 0) || (endingWindowRemoved != null && endingWindowRemoved > 0)) {
            log.info("event=auction.active_index.cleanup activeRemovedCount={} endingWindowRemovedCount={} threshold={}",
                    removed == null ? 0 : removed, endingWindowRemoved == null ? 0 : endingWindowRemoved, threshold);
        }
    }
}
