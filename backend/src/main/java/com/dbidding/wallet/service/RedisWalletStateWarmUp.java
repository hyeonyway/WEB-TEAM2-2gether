package com.dbidding.wallet.service;

import com.dbidding.wallet.repository.WalletHoldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;

/** 기동 시 현재 자금이 묶여있는(HELD) 지갑만 제한적으로 Redis에 준비한다. */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisWalletStateWarmUp {
    private final WalletHoldRepository walletHoldRepository;
    private final RedisWalletStateSeeder stateSeeder;

    @Bean("redisWalletStateWarmUpRunner")
    ApplicationRunner redisWalletStateWarmUpRunner(
            @Value("${auction.state-seeding.wallet-warm-up.enabled:true}") boolean enabled,
            @Value("${auction.state-seeding.wallet-warm-up.recent-limit:200}") int recentLimit
    ) {
        return arguments -> {
            if (!enabled || recentLimit <= 0) return;
            java.util.List<Integer> userIds = walletHoldRepository.findDistinctHeldUserIds(PageRequest.of(0, recentLimit));
            stateSeeder.seedAllIfAbsent(userIds);
        };
    }
}
