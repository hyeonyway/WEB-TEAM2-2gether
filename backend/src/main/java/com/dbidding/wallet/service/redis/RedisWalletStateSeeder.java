package com.dbidding.wallet.service.redis;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletHeldHoldRow;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
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
    private final RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    private final RedisStateSingleFlight singleFlight;
    private final RedisWalletSeedBatchCoordinator batchCoordinator;
    @Qualifier("walletBootstrapScript")
    private final RedisScript<Long> walletBootstrapScript;

    public void seedIfAbsent(Integer userId) {
        String key = "wallet:balance:" + userId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return;
        singleFlight.execute(key, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return false;
            if (!projectionCatchUpVerifier.isCaughtUpForUserFresh(userId)) throw AuctionException.stateRecoveryRequired();
            batchCoordinator.requestSeedData(userId).join()
                    .ifPresent(seedData -> seed(seedData.wallet(), seedData.holds()));
            return true;
        });
    }

    /** 기동 시 warm-up처럼, 호출자가 이미 들고 있는 userId 목록을 배치 조회 1회로 시딩한다. */
    public void seedAllIfAbsent(List<Integer> userIds) {
        if (userIds.isEmpty() || !projectionCatchUpVerifier.isCaughtUp()) return;
        java.util.Map<Integer, WalletSeedData> resolved = WalletSeedData.resolveBatch(userIds, walletHoldRepository, walletRepository);
        userIds.forEach(userId -> {
            WalletSeedData seedData = resolved.get(userId);
            if (seedData != null) seed(seedData.wallet(), seedData.holds());
        });
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
