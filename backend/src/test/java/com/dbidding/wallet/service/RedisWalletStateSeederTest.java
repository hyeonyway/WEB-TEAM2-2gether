package com.dbidding.wallet.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletRepository;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import com.dbidding.wallet.repository.WalletHoldRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisWalletStateSeederTest {
    @Test
    void Redis_지갑_state가_없을때만_MySQL_projection으로_초기화한다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
        RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        WalletBootstrapRow row = Mockito.mock(WalletBootstrapRow.class);
        when(row.getUserId()).thenReturn(7);
        when(row.getPoint()).thenReturn(100_000L);
        when(row.getFrozenBalance()).thenReturn(30_000L);
        when(row.getProjectionVersion()).thenReturn(4L);
        when(walletRepository.findBootstrapRowsForUsers(List.of(7))).thenReturn(List.of(row));
        when(walletHoldRepository.findHeldRowsForUsers(List.of(7))).thenReturn(List.of());
        when(redisTemplate.hasKey("wallet:balance:7")).thenReturn(false);
        when(projectionCatchUpVerifier.isCaughtUp()).thenReturn(true);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, singleFlight, batchCoordinator, script
        ).seedIfAbsent(7);

        verify(redisTemplate).execute(script, List.of("wallet:balance:7"), "70000", "30000", "4");
    }

    @Test
    void seedAllIfAbsent은_caughtUp이_아니면_아무_쿼리도_하지_않는다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
        RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        when(projectionCatchUpVerifier.isCaughtUp()).thenReturn(false);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, singleFlight, batchCoordinator, script
        ).seedAllIfAbsent(List.of(2, 5, 7));

        Mockito.verifyNoInteractions(walletRepository, walletHoldRepository);
    }

    @Test
    void seedAllIfAbsent은_userId_목록을_배치_쿼리_한_번으로_시딩한다() {
        WalletRepository walletRepository = Mockito.mock(WalletRepository.class);
        WalletHoldRepository walletHoldRepository = Mockito.mock(WalletHoldRepository.class);
        StringRedisTemplate redisTemplate = Mockito.mock(StringRedisTemplate.class);
        RedisProjectionCatchUpVerifier projectionCatchUpVerifier = Mockito.mock(RedisProjectionCatchUpVerifier.class);
        RedisStateSingleFlight singleFlight = new RedisStateSingleFlight();
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        WalletBootstrapRow row2 = Mockito.mock(WalletBootstrapRow.class);
        when(row2.getUserId()).thenReturn(2);
        when(row2.getPoint()).thenReturn(50_000L);
        when(row2.getFrozenBalance()).thenReturn(0L);
        when(row2.getProjectionVersion()).thenReturn(1L);
        // userId 5는 wallet row가 아예 없는 유저 — 조용히 스킵되어야 함
        when(walletRepository.findBootstrapRowsForUsers(List.of(2, 5))).thenReturn(List.of(row2));
        when(walletHoldRepository.findHeldRowsForUsers(List.of(2, 5))).thenReturn(List.of());
        when(projectionCatchUpVerifier.isCaughtUp()).thenReturn(true);
        RedisWalletSeedBatchCoordinator batchCoordinator = new RedisWalletSeedBatchCoordinator(walletHoldRepository, walletRepository, 5, 200);

        new RedisWalletStateSeeder(
                walletRepository, walletHoldRepository, redisTemplate, projectionCatchUpVerifier, singleFlight, batchCoordinator, script
        ).seedAllIfAbsent(List.of(2, 5));

        verify(walletRepository, Mockito.times(1)).findBootstrapRowsForUsers(List.of(2, 5));
        verify(walletHoldRepository, Mockito.times(1)).findHeldRowsForUsers(List.of(2, 5));
        verify(redisTemplate).execute(script, List.of("wallet:balance:2"), "50000", "0", "1");
    }
}
