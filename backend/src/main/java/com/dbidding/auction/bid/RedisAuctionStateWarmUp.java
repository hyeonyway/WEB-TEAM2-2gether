package com.dbidding.auction.bid;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
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

/** 기동 시 마감 임박 활성 경매만 제한적으로 Redis에 준비한다. */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionStateWarmUp {
    private final AuctionRepository auctionRepository;
    private final RedisAuctionStateSeeder stateSeeder;
    private final Clock clock;

    @Bean
    ApplicationRunner redisAuctionStateWarmUp(
            @Value("${auction.state-seeding.warm-up.enabled:true}") boolean enabled,
            @Value("${auction.state-seeding.warm-up.closing-window-minutes:30}") long closingWindowMinutes,
            @Value("${auction.state-seeding.warm-up.recent-limit:100}") int recentLimit
    ) {
        return arguments -> {
            if (!enabled || recentLimit < 1) return;
            var now = clock.instant();
            var candidates = new LinkedHashMap<Integer, com.dbidding.auction.domain.Auction>();
            auctionRepository.findActiveForWarmUp(
                    List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), now, now.plus(Duration.ofMinutes(closingWindowMinutes)),
                    PageRequest.of(0, recentLimit)
            ).forEach(auction -> candidates.put(auction.getId(), auction));
            if (candidates.size() < recentLimit) auctionRepository.findByStatusInOrderByOpenTimeDesc(
                    List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), PageRequest.of(0, recentLimit)
            ).forEach(auction -> candidates.putIfAbsent(auction.getId(), auction));
            stateSeeder.seedAllIfAbsent(candidates.values().stream().limit(recentLimit).toList());
        };
    }
}
