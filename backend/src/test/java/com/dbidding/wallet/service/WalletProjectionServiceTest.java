package com.dbidding.wallet.service;

import static org.mockito.Mockito.*;
import com.dbidding.auction.stream.WalletStateChangedStreamEvent;
import com.dbidding.wallet.domain.PointTransactionType;
import com.dbidding.wallet.domain.Wallet;
import com.dbidding.wallet.repository.PointRecordRepository;
import com.dbidding.wallet.repository.WalletHoldRepository;
import com.dbidding.wallet.repository.WalletRepository;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class WalletProjectionServiceTest {
    @Test void 재전달된_원장_이벤트는_중복_저장하지_않는다() {
        WalletRepository wallets = mock(WalletRepository.class);
        PointRecordRepository records = mock(PointRecordRepository.class);
        WalletHoldRepository holds = mock(WalletHoldRepository.class);
        Wallet wallet = mock(Wallet.class);
        when(wallets.findByUserId(1)).thenReturn(Optional.of(wallet));
        when(wallet.getId()).thenReturn(10);
        UUID eventId = UUID.randomUUID();
        when(records.existsByEventId(eventId)).thenReturn(true);

        new WalletProjectionService(wallets, records, holds).project(new WalletStateChangedStreamEvent(
                "1-0", eventId, "wallet.charged.v1", 1, 2L, 10_000L, 0L,
                null, null, null, PointTransactionType.CHARGE, 10_000L, "key", Instant.now()));

        verify(records, never()).save(any());
        verify(wallets).updateProjectionIfNewer(1, 10_000L, 2L);
    }

    @Test
    void 현재_projection보다_낮은_Stream_이벤트는_지갑_SSE를_발행하지_않는다() {
        WalletRepository wallets = mock(WalletRepository.class);
        PointRecordRepository records = mock(PointRecordRepository.class);
        WalletHoldRepository holds = mock(WalletHoldRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        Wallet wallet = mock(Wallet.class);
        when(wallets.findByUserId(1)).thenReturn(Optional.of(wallet));
        when(wallet.getId()).thenReturn(10);
        when(wallets.updateProjectionIfNewer(1, 10_000L, 2L)).thenReturn(0);

        new WalletProjectionService(wallets, records, holds, events,
                Clock.fixed(Instant.parse("2026-08-12T00:00:00Z"), ZoneOffset.UTC))
                .project(new WalletStateChangedStreamEvent(
                        "1-0", UUID.randomUUID(), "wallet.charged.v1", 1, 2L, 10_000L, 0L,
                        null, null, null, PointTransactionType.CHARGE, 10_000L, "key", Instant.now()));

        verify(events, never()).publishEvent(any());
    }
}
