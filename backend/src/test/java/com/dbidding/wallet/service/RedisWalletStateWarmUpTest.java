package com.dbidding.wallet.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.wallet.repository.WalletHoldRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class RedisWalletStateWarmUpTest {
    @Test
    void 활성화_상태면_HELD_유저를_배치로_시딩한다() throws Exception {
        WalletHoldRepository walletHoldRepository = mock(WalletHoldRepository.class);
        RedisWalletStateSeeder stateSeeder = mock(RedisWalletStateSeeder.class);
        when(walletHoldRepository.findDistinctHeldUserIds(PageRequest.of(0, 200))).thenReturn(List.of(2, 5, 7));
        RedisWalletStateWarmUp warmUp = new RedisWalletStateWarmUp(walletHoldRepository, stateSeeder);

        warmUp.redisWalletStateWarmUpRunner(true, 200).run(null);

        verify(stateSeeder).seedAllIfAbsent(List.of(2, 5, 7));
    }

    @Test
    void 비활성이면_아무것도_조회하거나_시딩하지_않는다() throws Exception {
        WalletHoldRepository walletHoldRepository = mock(WalletHoldRepository.class);
        RedisWalletStateSeeder stateSeeder = mock(RedisWalletStateSeeder.class);
        RedisWalletStateWarmUp warmUp = new RedisWalletStateWarmUp(walletHoldRepository, stateSeeder);

        warmUp.redisWalletStateWarmUpRunner(false, 200).run(null);

        verify(walletHoldRepository, never()).findDistinctHeldUserIds(org.mockito.ArgumentMatchers.any());
        verify(stateSeeder, never()).seedAllIfAbsent(org.mockito.ArgumentMatchers.anyList());
    }
}
