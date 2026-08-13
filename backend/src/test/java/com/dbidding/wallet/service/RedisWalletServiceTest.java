package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisWalletServiceTest {
    private final WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
    private final StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, Object, Object> hashOperations = Mockito.mock(HashOperations.class);
    @SuppressWarnings("unchecked")
    private final RedisScript<String> walletTransitionScript = Mockito.mock(RedisScript.class);
    private final RedisWalletStateSeeder stateSeeder = Mockito.mock(RedisWalletStateSeeder.class);
    private final RedisWalletService walletService = new RedisWalletService(
            walletRepository, Mockito.mock(PointRecordRepository.class), Mockito.mock(WalletHoldRepository.class),
            Mockito.mock(com.dbidding.wallet.metrics.WalletMetrics.class), Clock.systemUTC(),
            Mockito.mock(ApplicationEventPublisher.class), redisTemplate, walletTransitionScript, stateSeeder
    );

    @Test
    void 지갑_생성시_잔액_hash에_1시간에서_6시간_사이_idle_TTL을_건다() {
        when(walletRepository.existsByUserId(7)).thenReturn(false);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        walletService.provision(7);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(redisTemplate).expire(org.mockito.ArgumentMatchers.eq("wallet:balance:7"), ttl.capture());
        assertThat(ttl.getValue().getSeconds()).isBetween(3600L, 21600L);
    }
}
