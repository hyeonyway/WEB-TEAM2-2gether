package com.dbidding.auction.bid;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionEndingPolicy;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 기동 시 마감 임박 활성 경매만 제한적으로 Redis에 준비한다. */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionStateWarmUp {
    private final AuctionRepository auctionRepository;
    private final RedisAuctionStateSeeder stateSeeder;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Bean("redisAuctionStateWarmUpRunner")
    ApplicationRunner redisAuctionStateWarmUpRunner(
            @Value("${auction.state-seeding.warm-up.enabled:true}") boolean enabled,
            @Value("${auction.state-seeding.warm-up.closing-window-minutes:30}") long closingWindowMinutes,
            @Value("${auction.state-seeding.warm-up.recent-limit:100}") int recentLimit,
            @Value("${auction.state-seeding.warm-up.ending-window-repair-limit:1000}") int endingWindowRepairLimit
    ) {
        return arguments -> {
            if (enabled && recentLimit > 0) {
                var now = clock.instant();
                var candidates = new LinkedHashMap<Integer, com.dbidding.auction.domain.Auction>();
                auctionRepository.findActiveForWarmUp(
                        List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), now.plus(Duration.ofMinutes(closingWindowMinutes)),
                        PageRequest.of(0, recentLimit)
                ).forEach(auction -> candidates.put(auction.getId(), auction));
                if (candidates.size() < recentLimit) auctionRepository.findByStatusInOrderByOpenTimeDesc(
                        List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), PageRequest.of(0, recentLimit)
                ).forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
                stateSeeder.seedAllIfAbsent(candidates.values().stream().limit(recentLimit).toList());
            }
            repairEndingWindow(Math.max(endingWindowRepairLimit, 0));
        };
    }

    private void repairEndingWindow(int limit) {
        if (limit == 0) return;
        java.util.Set<String> auctionIds = redisTemplate.opsForZSet().range("auction:active:by-close-time", 0, limit - 1);
        if (auctionIds == null || auctionIds.isEmpty()) return;
        for (String auctionId : auctionIds) {
            String stateKey = "auction:state:" + auctionId;
            java.util.Map<Object, Object> fields = redisTemplate.opsForHash().entries(stateKey);
            String status = text(fields.get("status"));
            if (!"OPEN".equals(status) && !"ENDING".equals(status)) {
                redisTemplate.opsForZSet().remove("auction:ending-window:by-close-time", auctionId);
                continue;
            }
            String closeTime = text(fields.get("closeTime"));
            String closeTimeEpochMillis = text(fields.get("closeTimeEpochMillis"));
            if (closeTime == null || closeTimeEpochMillis == null) {
                redisTemplate.opsForZSet().remove("auction:ending-window:by-close-time", auctionId);
                continue;
            }
            String estimatedEpochMillis = text(fields.get("estimatedCloseTimeEpochMillis"));
            if (text(fields.get("estimatedCloseTime")) == null || estimatedEpochMillis == null) {
                redisTemplate.opsForHash().put(stateKey, "estimatedCloseTime", closeTime);
                redisTemplate.opsForHash().put(stateKey, "estimatedCloseTimeEpochMillis", closeTimeEpochMillis);
                estimatedEpochMillis = closeTimeEpochMillis;
            }
            if ("OPEN".equals(status)) {
                redisTemplate.opsForZSet().add("auction:ending-window:by-close-time", auctionId,
                        Long.parseLong(estimatedEpochMillis) - AuctionEndingPolicy.WINDOW.toMillis());
            } else {
                redisTemplate.opsForZSet().remove("auction:ending-window:by-close-time", auctionId);
            }
        }
    }

    private String text(Object value) {
        return value == null || value.toString().isBlank() ? null : value.toString();
    }
}
