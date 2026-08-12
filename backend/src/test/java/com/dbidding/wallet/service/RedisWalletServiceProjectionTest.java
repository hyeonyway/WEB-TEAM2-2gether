package com.dbidding.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.metrics.WalletMetrics;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class RedisWalletServiceProjectionTest {

    @Test
    void Redis_입찰_projection의_hold는_DB_지갑_버전과_SSE를_변경하지_않는다() {
        WalletRepository wallets = org.mockito.Mockito.mock(WalletRepository.class);
        PointRecordRepository records = org.mockito.Mockito.mock(PointRecordRepository.class);
        WalletHoldRepository holds = org.mockito.Mockito.mock(WalletHoldRepository.class);
        ApplicationEventPublisher events = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        Wallet wallet = org.mockito.Mockito.spy(Wallet.open(1));
        given(wallet.getId()).willReturn(10);
        wallet.credit(10_000L);
        given(wallets.findByUserIdForUpdate(1)).willReturn(Optional.of(wallet));
        given(wallets.sumHeldAmount(any())).willReturn(0L);
        RedisWalletService service = new RedisWalletService(
                wallets, records, holds, new WalletMetrics(new SimpleMeterRegistry()),
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC), events,
                org.mockito.Mockito.mock(StringRedisTemplate.class), org.mockito.Mockito.mock(RedisScript.class),
                org.mockito.Mockito.mock(RedisWalletStateSeeder.class));

        service.hold(1, 100, 10_000L);

        assertThat(wallet.getProjectionVersion()).isZero();
        then(events).should(never()).publishEvent(any());
    }
}
