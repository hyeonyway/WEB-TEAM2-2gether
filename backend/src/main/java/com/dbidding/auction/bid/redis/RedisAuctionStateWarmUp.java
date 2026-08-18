package com.dbidding.auction.bid.redis;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionEndingPolicy;
import com.dbidding.wallet.service.redis.RedisWalletStateSeeder;
import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 기동 시 활성(OPEN/ENDING) 경매를 전부(안전 상한 이내) Redis에 준비한다. 예전에는 마감임박/최근
 * 개설과 정렬 기준(입찰수/가격/변동률)별 상위권만 따로 골라 warm-up했는데, 그 범위를 벗어난
 * 경매는 목록 결과에서 조용히 누락되거나 첫 접근 때 온디맨드 콜드시드를 타야 했다. 활성 경매
 * 전체를 warm-up하면 애초에 어떤 정렬 기준으로 조회하든 커버리지 문제가 생기지 않는다.
 *
 * <p>지갑 warm-up은 더 이상 독립 설정으로 켜고 끌 수 없다: 이번에 warm-up한 경매의 낙찰
 * 후보 지갑만 함께 올리므로, {@code auction.state-seeding.warm-up.enabled}(과거
 * {@code AUCTION_STATE_WARM_UP_ENABLED})가 false면 지갑 warm-up도 같이 꺼진다.</p>
 *
 * <p>이 빈은 {@link org.springframework.boot.ApplicationRunner}라 {@code ApplicationReadyEvent}
 * 직전에 실행된다 — Spring Boot는 기본적으로 모든 {@code ApplicationRunner}/{@code CommandLineRunner}가
 * 끝난 뒤에야 readiness 상태를 {@code ACCEPTING_TRAFFIC}으로 바꾸므로, 이 warm-up이 끝나기 전까지
 * {@code /actuator/health/readiness}는 계속 준비 안 됨으로 응답한다. 배포 스크립트가 이 readiness
 * 엔드포인트를 폴링해 nginx 전환 시점을 결정하므로, 여기서 별도로 readiness 이벤트를 발행할
 * 필요는 없다.</p>
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
            @Value("${auction.state-seeding.warm-up.max-active-auctions:50000}") int maxActiveAuctions,
            @Value("${auction.state-seeding.warm-up.ending-window-repair-limit:1000}") int endingWindowRepairLimit
    ) {
        return arguments -> {
            if (enabled && maxActiveAuctions > 0) {
                var statuses = List.of(AuctionStatus.OPEN, AuctionStatus.ENDING);
                List<com.dbidding.auction.domain.Auction> activeAuctions =
                        auctionRepository.findByStatusInOrderByOpenTimeDesc(statuses, PageRequest.of(0, maxActiveAuctions));
                List<Integer> leadingBidderIds = stateSeeder.seedAllIfAbsent(activeAuctions);
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
