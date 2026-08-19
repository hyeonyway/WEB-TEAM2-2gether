package com.dbidding.auction.service;

import com.dbidding.global.redis.RedisKeyScanner;
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

/**
 * auction:state는 경매 종료 시 TTL이 걸려 사라지지만, 사용자별 입찰 상태(auction:bidder:*),
 * 최근 입찰 스트림(auction:recent-bids:*), 참여 목록(auction:dashboard:participating:*)은
 * 만료 로직이 없어 영구 잔존한다.
 *
 * <p>이 키들은 입찰 hot path(bid-accept.lua)에서 어떤 auctionId가 어떤 bidderId와 묶이는지
 * 알려주는 역인덱스가 없어 종료 시점에 즉시 정리할 수 없다. 대신 auction-active-index-gc와 같은
 * 방식의 안전망으로, state가 이미 사라진(=경매가 끝난 지 오래된) 항목만 SCAN으로 찾아 늦게라도
 * 정리한다.</p>
 */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
class RedisBidderStateCleanupScheduler {
    private static final String BIDDER_KEY_PATTERN = "auction:bidder:*";
    private static final String RECENT_BIDS_KEY_PATTERN = "auction:recent-bids:*";
    private static final String PARTICIPATING_KEY_PATTERN = "auction:dashboard:participating:*";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("auctionBidderStateGcScript") private final RedisScript<Long> auctionBidderStateGcScript;
    @Qualifier("auctionDashboardParticipatingGcScript") private final RedisScript<Long> auctionDashboardParticipatingGcScript;
    @Value("${auction.bidder-state.cleanup.scan-limit:1000000}") private int scanLimit;
    @Value("${auction.bidder-state.cleanup.batch-size:200}") private int batchSize;
    /** participating은 SET이라 키 하나당 비용이 멤버 수에 비례한다 - 플랫 키(bidder/recent-bids)보다
     * 훨씬 작은 배치로 묶어 GC Lua 호출 1회가 Redis를 오래 블로킹하지 않게 한다. */
    @Value("${auction.bidder-state.cleanup.participating-batch-size:20}") private int participatingBatchSize;

    @Scheduled(fixedDelayString = "${auction.bidder-state.cleanup.fixed-delay-ms:3600000}")
    @SchedulerLock(
            name = "auction-bidder-state-cleanup",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT30M"
    )
    void removeOrphanedBidderState() {
        long bidderRemoved = RedisKeyScanner.scanAndCleanup(redisTemplate, BIDDER_KEY_PATTERN, scanLimit, batchSize, auctionBidderStateGcScript, this::auctionIdOf);
        long recentBidsRemoved = RedisKeyScanner.scanAndCleanup(redisTemplate, RECENT_BIDS_KEY_PATTERN, scanLimit, batchSize, auctionBidderStateGcScript, this::auctionIdOf);
        long participatingRemoved = RedisKeyScanner.scanAndCleanup(redisTemplate, PARTICIPATING_KEY_PATTERN, scanLimit, participatingBatchSize, auctionDashboardParticipatingGcScript);
        if (bidderRemoved > 0 || recentBidsRemoved > 0 || participatingRemoved > 0) {
            log.info("event=auction.bidder_state.cleanup bidderRemovedCount={} recentBidsRemovedCount={} participatingRemovedCount={}",
                    bidderRemoved, recentBidsRemoved, participatingRemoved);
        }
    }

    /** auction:bidder:{auctionId}:{bidderId}, auction:recent-bids:{auctionId} 모두 세 번째 세그먼트가 auctionId다. */
    private String auctionIdOf(String key) {
        return key.split(":")[2];
    }
}
