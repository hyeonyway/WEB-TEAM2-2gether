package com.dbidding.wallet.service;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Redis가 비어 있을 때 MySQL projection에서 지갑 승인 상태를 다시 만든다. */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisWalletBootstrap {
    private final WalletRepository walletRepository;
    private final StringRedisTemplate redisTemplate;

    @Bean
    ApplicationRunner redisWalletStateBootstrap() {
        return arguments -> walletRepository.findAll().forEach(this::seedIfAbsent);
    }

    private void seedIfAbsent(Wallet wallet) {
        String key = "wallet:balance:" + wallet.getUserId();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return;
        long frozen = walletRepository.sumHeldAmount(wallet.getId());
        redisTemplate.opsForHash().putAll(key, java.util.Map.of(
                "availableBalance", String.valueOf(wallet.getPoint() - frozen),
                "frozenBalance", String.valueOf(frozen),
                "walletVersion", String.valueOf(wallet.getProjectionVersion())
        ));
    }
}
