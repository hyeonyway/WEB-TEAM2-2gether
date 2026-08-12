package com.dbidding.wallet.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.wallet.repository.WalletBootstrapRow;
import com.dbidding.wallet.repository.WalletRepository;
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
        @SuppressWarnings("unchecked") RedisScript<Long> script = Mockito.mock(RedisScript.class);
        WalletBootstrapRow row = Mockito.mock(WalletBootstrapRow.class);
        when(row.getUserId()).thenReturn(7);
        when(row.getPoint()).thenReturn(100_000L);
        when(row.getFrozenBalance()).thenReturn(30_000L);
        when(row.getProjectionVersion()).thenReturn(4L);
        when(walletRepository.findBootstrapRowsForUsers(List.of(7))).thenReturn(List.of(row));
        when(walletHoldRepository.findHeldRowsForUsers(List.of(7))).thenReturn(List.of());

        new RedisWalletStateSeeder(walletRepository, walletHoldRepository, redisTemplate, script).seedIfAbsent(7);

        verify(redisTemplate).execute(script, List.of("wallet:balance:7"), "70000", "30000", "4");
    }
}
