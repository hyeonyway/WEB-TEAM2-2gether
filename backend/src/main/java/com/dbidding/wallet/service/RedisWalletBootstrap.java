package com.dbidding.wallet.service;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;

/** Redis가 비어 있을 때 MySQL projection에서 지갑 승인 상태를 다시 만든다. */
@Configuration
@Profile("redis")
@RequiredArgsConstructor
public class RedisWalletBootstrap {
    private final WalletRepository walletRepository;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("walletBootstrapScript")
    private final RedisScript<Long> walletBootstrapScript;

    @Bean
    ApplicationRunner redisWalletStateBootstrap() {
        return arguments -> {
            int page = 0;
            org.springframework.data.domain.Page<WalletBootstrapRow> rows;
            do {
                rows = walletRepository.findBootstrapRows(PageRequest.of(page++, 500));
                rows.forEach(this::seedIfOlder);
            } while (rows.hasNext());
        };
    }

    private void seedIfOlder(WalletBootstrapRow wallet) {
        long available = wallet.getPoint() - wallet.getFrozenBalance();
        redisTemplate.execute(walletBootstrapScript, java.util.List.of("wallet:balance:" + wallet.getUserId()),
                String.valueOf(available), String.valueOf(wallet.getFrozenBalance()), String.valueOf(wallet.getProjectionVersion()));
    }
}
