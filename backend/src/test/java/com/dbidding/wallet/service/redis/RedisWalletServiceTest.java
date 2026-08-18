package com.dbidding.wallet.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.wallet.dto.WalletTransactionResponse;
import com.dbidding.wallet.exception.InvalidWalletAmountException;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
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
    void 지갑_생성시_잔액_hash에_TTL을_걸지_않는다() {
        when(walletRepository.existsByUserId(7)).thenReturn(false);
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        walletService.provision(7);

        verify(redisTemplate, never()).expire(org.mockito.ArgumentMatchers.eq("wallet:balance:7"), any(Duration.class));
    }

    @Test
    void 잔액_조회도_TTL을_걸지_않는다() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("wallet:balance:7", "availableBalance")).thenReturn("10000");
        when(hashOperations.get("wallet:balance:7", "frozenBalance")).thenReturn("0");
        when(hashOperations.get("wallet:balance:7", "walletVersion")).thenReturn("1");

        walletService.getBalance(7);

        verify(redisTemplate, never()).expire(eq("wallet:balance:7"), any(Duration.class));
    }

    @Test
    void 과거_멱등_응답의_지수_표기_잔액을_exact_long으로_복구한다() {
        when(redisTemplate.execute(eq(walletTransitionScript), anyList(), any(Object[].class)))
                .thenReturn("ACCEPTED|1-0|1.000000512e+14|0|4|true");

        WalletTransactionResponse response = walletService.charge(7, 100_000_000_000L, "legacy-key");

        assertThat(response.balance()).isEqualTo(100_000_051_200_000L);
    }

    @Test
    void 일회_충전_상한을_넘으면_Redis를_호출하지_않는다() {
        assertThatThrownBy(() -> walletService.charge(7, 100_000_000_001L, "over-limit"))
                .isInstanceOf(InvalidWalletAmountException.class);

        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    void Redis_잔액_합계가_long_범위를_넘으면_음수로_반환하지_않는다() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.get("wallet:balance:7", "availableBalance")).thenReturn(Long.toString(Long.MAX_VALUE));
        when(hashOperations.get("wallet:balance:7", "frozenBalance")).thenReturn("1");
        when(hashOperations.get("wallet:balance:7", "walletVersion")).thenReturn("1");

        assertThatThrownBy(() -> walletService.getBalance(7))
                .isInstanceOf(ArithmeticException.class);
    }
}
