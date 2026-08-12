package com.dbidding.wallet.service;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletHeldHoldRow;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** Redis state miss 때만 MySQL 지갑 projection을 조건부로 초기화한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisWalletStateSeeder {
    private final WalletRepository walletRepository;
    private final WalletHoldRepository walletHoldRepository;
    private final StringRedisTemplate redisTemplate;
    @Qualifier("walletBootstrapScript")
    private final RedisScript<Long> walletBootstrapScript;

    public void seedIfAbsent(Integer userId) {
        List<WalletHeldHoldRow> holds = walletHoldRepository.findHeldRowsForUsers(List.of(userId));
        walletRepository.findBootstrapRowsForUsers(List.of(userId)).stream().findFirst().ifPresent(wallet -> seed(wallet, holds));
    }

    private void seed(WalletBootstrapRow wallet, List<WalletHeldHoldRow> holds) {
        long available = wallet.getPoint() - wallet.getFrozenBalance();
        List<String> keys = new java.util.ArrayList<>(List.of("wallet:balance:" + wallet.getUserId()));
        List<String> arguments = new java.util.ArrayList<>(List.of(
                String.valueOf(available), String.valueOf(wallet.getFrozenBalance()), String.valueOf(wallet.getProjectionVersion())
        ));
        holds.forEach(hold -> {
            keys.add("wallet:hold:" + hold.getAuctionId() + ':' + hold.getUserId());
            arguments.add(String.valueOf(hold.getAmount()));
        });
        redisTemplate.execute(walletBootstrapScript, keys, arguments.toArray());
    }
}
