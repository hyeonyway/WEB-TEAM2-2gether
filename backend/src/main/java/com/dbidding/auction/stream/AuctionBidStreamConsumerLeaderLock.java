package com.dbidding.auction.stream;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/**
 * Stream Consumer의 실행 리더를 Redis 원자 락으로 하나만 선출한다.
 *
 * <p>Consumer Group은 메시지 분배용이며 같은 애플리케이션 인스턴스가 여러 대일 때 DB 트랜잭션을
 * 동시에 실행하지 않는다는 보장은 제공하지 않는다. 이 락은 ShedLock의 단일 실행 효과를 Consumer
 * poll 단위에 적용한다.</p>
 */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class AuctionBidStreamConsumerLeaderLock {
    static final String KEY = "auction:bid-events:consumer-leader-lock:v1";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final AuctionBidStreamProperties properties;
    private final String ownerToken = UUID.randomUUID().toString();
    private boolean leader;

    public synchronized boolean tryAcquire() {
        Duration lease = properties.consumerLockAtMostFor();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(KEY, ownerToken, lease);
        leader = Boolean.TRUE.equals(acquired);
        return leader;
    }

    public synchronized void releaseAfterRun() {
        release();
    }

    @PreDestroy
    synchronized void release() {
        if (!leader) {
            return;
        }
        redisTemplate.execute(RELEASE_SCRIPT, List.of(KEY), ownerToken);
        leader = false;
    }
}
