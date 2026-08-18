package com.dbidding.wallet.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;

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
import com.dbidding.wallet.sse.WalletBalanceChangedEvent;

class RedisWalletServiceProjectionTest {

    @Test
    void 충전_Redis_승인_직후_지갑_SSE_이벤트를_발행한다() {
        WalletRepository wallets = org.mockito.Mockito.mock(WalletRepository.class);
        PointRecordRepository records = org.mockito.Mockito.mock(PointRecordRepository.class);
        WalletHoldRepository holds = org.mockito.Mockito.mock(WalletHoldRepository.class);
        ApplicationEventPublisher events = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        StringRedisTemplate redis = org.mockito.Mockito.mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") RedisScript<String> script = org.mockito.Mockito.mock(RedisScript.class);
        RedisWalletStateSeeder seeder = org.mockito.Mockito.mock(RedisWalletStateSeeder.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC);
        RedisWalletService service = new RedisWalletService(
                wallets, records, holds, new WalletMetrics(new SimpleMeterRegistry()), clock, events,
                redis, script, seeder);
        when(redis.execute(eq(script), eq(java.util.List.of(
                        "wallet:balance:1", "wallet:idempotency:1:charge-key", "event:timeline")),
                anyString(), eq("wallet.charged.v1"), eq("1"), eq("3000"), eq("charge-key"),
                eq("wallet.charged.v1:3000"), eq("2026-08-12T00:00:00Z"),
                eq("100000000000"), eq("1000000000000")))
                .thenReturn("ACCEPTED|1-0|1.3000e+4|2.000e+3|1.00000000000000e+14|false");

        service.charge(1, 3_000L, "charge-key");

        verify(events).publishEvent(argThat((Object event) -> event instanceof WalletBalanceChangedEvent changed
                && changed.userId().equals(1)
                && changed.balance().availableBalance() == 13_000L
                && changed.balance().frozenBalance() == 2_000L
                && changed.walletVersion() == 100_000_000_000_000L));
    }

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
