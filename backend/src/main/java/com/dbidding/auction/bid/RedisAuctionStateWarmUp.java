package com.dbidding.auction.bid;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionEndingPolicy;
import com.dbidding.wallet.service.RedisWalletStateSeeder;
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

/**
 * 기동 시 두 종류의 활성 경매를 제한적으로 Redis에 준비한다: (1) 마감 임박/최근 개설처럼
 * 입찰 진행에 곧바로 영향을 주는 경매, (2) 목록 조회의 각 정렬 기준(입찰수/가격/변동률)
 * 상위권처럼 목록에 바로 노출돼야 하는 경매. 두 번째를 빼먹으면, 정렬 ZSET에 아예 항목이
 * 없는 활성 경매는 warm-up 대상도 아니고 목록에서 클릭된 적도 없어 발견될 방법이 없어서
 * 목록 결과에서 조용히 누락된다.
 */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionStateWarmUp {
    private final AuctionRepository auctionRepository;
    private final RedisAuctionStateSeeder stateSeeder;
    private final RedisWalletStateSeeder walletStateSeeder;
    private final StringRedisTemplate redisTemplate;
    private final Clock clock;

    @Bean("redisAuctionStateWarmUpRunner")
    ApplicationRunner redisAuctionStateWarmUpRunner(
            @Value("${auction.state-seeding.warm-up.enabled:true}") boolean enabled,
            @Value("${auction.state-seeding.warm-up.closing-window-minutes:30}") long closingWindowMinutes,
            @Value("${auction.state-seeding.warm-up.recent-limit:500}") int recentLimit,
            @Value("${auction.state-seeding.warm-up.list-coverage-limit:500}") int listCoverageLimit,
            @Value("${auction.state-seeding.warm-up.ending-window-repair-limit:1000}") int endingWindowRepairLimit
    ) {
        return arguments -> {
            if (enabled && (recentLimit > 0 || listCoverageLimit > 0)) {
                var now = clock.instant();
                var statuses = List.of(AuctionStatus.OPEN, AuctionStatus.ENDING);
                var candidates = new LinkedHashMap<Integer, com.dbidding.auction.domain.Auction>();
                if (recentLimit > 0) {
                    auctionRepository.findActiveForWarmUp(statuses, now.plus(Duration.ofMinutes(closingWindowMinutes)), PageRequest.of(0, recentLimit))
                            .forEach(auction -> candidates.put(auction.getId(), auction));
                    if (candidates.size() < recentLimit) auctionRepository.findByStatusInOrderByOpenTimeDesc(statuses, PageRequest.of(0, recentLimit))
                            .forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
                }
                if (listCoverageLimit > 0) {
                    // 정렬 기준별 목록 첫 페이지가 항상 채워지도록, 각 정렬의 상위권 경매도 warm-up 대상에 넣는다.
                    // PRICE_HIGH/PRICE_LOW는 같은 ZSET을 방향만 다르게 읽으므로 양끝 모두 필요하다.
                    auctionRepository.findByStatusInOrderByBidCountDesc(statuses, PageRequest.of(0, listCoverageLimit))
                            .forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
                    auctionRepository.findByStatusInOrderByCurrentPriceDesc(statuses, PageRequest.of(0, listCoverageLimit))
                            .forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
                    auctionRepository.findByStatusInOrderByCurrentPriceAsc(statuses, PageRequest.of(0, listCoverageLimit))
                            .forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
                    auctionRepository.findByStatusInOrderByChangeRateBasisPointsDesc(statuses, PageRequest.of(0, listCoverageLimit))
                            .forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
                }
                List<Integer> leadingBidderIds = stateSeeder.seedAllIfAbsent(candidates.values().stream().toList());
                // 지금 warm-up한 경매들의 낙찰 후보 지갑도 함께 올려서, 재기동 직후 첫 입찰이
                // 경매/지갑 어느 쪽이든 콜드미스 없이 바로 처리되게 한다.
                walletStateSeeder.seedAllIfAbsent(leadingBidderIds);
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
