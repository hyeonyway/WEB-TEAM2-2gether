package com.dbidding.order;

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
 * order:state:{auctionId}는 주문이 COMPLETED/CANCELLED로 전이될 때 TTL이 걸려 사라지지만,
 * order:state:buyer:{userId}/order:state:seller:{userId} Set에서는 그 auctionId가 SREM되지
 * 않아 영구 잔존한다. 이 Set을 SCAN으로 찾아 order:state가 이미 사라진(=주문이 끝난 지 오래된)
 * auctionId만 안전망으로 정리한다 - auction 도메인의 RedisBidderStateCleanupScheduler와 동일한 방식이다.
 */
@Slf4j
@Component
@Profile("redis")
@RequiredArgsConstructor
class RedisOrderStateIndexCleanupScheduler {
    private static final String BUYER_INDEX_PATTERN = "order:state:buyer:*";
    private static final String SELLER_INDEX_PATTERN = "order:state:seller:*";

    private final StringRedisTemplate redisTemplate;
    @Qualifier("orderStateIndexGcScript") private final RedisScript<Long> orderStateIndexGcScript;
    @Value("${order.state-index.cleanup.scan-limit:1000000}") private int scanLimit;
    /** buyer/seller 인덱스는 SET이라 키 하나당 비용이 멤버 수에 비례한다 - 작은 배치로 묶어 GC
     * Lua 호출 1회가 Redis를 오래 블로킹하지 않게 한다. */
    @Value("${order.state-index.cleanup.batch-size:20}") private int batchSize;

    @Scheduled(fixedDelayString = "${order.state-index.cleanup.fixed-delay-ms:3600000}")
    @SchedulerLock(
            name = "order-state-index-cleanup",
            lockAtLeastFor = "PT10S",
            lockAtMostFor = "PT30M"
    )
    void removeOrphanedIndexEntries() {
        long buyerRemoved = RedisKeyScanner.scanAndCleanup(redisTemplate, BUYER_INDEX_PATTERN, scanLimit, batchSize, orderStateIndexGcScript);
        long sellerRemoved = RedisKeyScanner.scanAndCleanup(redisTemplate, SELLER_INDEX_PATTERN, scanLimit, batchSize, orderStateIndexGcScript);
        if (buyerRemoved > 0 || sellerRemoved > 0) {
            log.info("event=order.state_index.cleanup buyerRemovedCount={} sellerRemovedCount={}", buyerRemoved, sellerRemoved);
        }
    }
}
