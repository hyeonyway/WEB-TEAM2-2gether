package com.dbidding.wallet.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LocalWalletSsePublisherTest {

    @Test
    void local_sse도_DB에서_발급한_지갑_버전을_그대로_전달한다() {
        WalletSseConnectionManager connectionManager = mock(WalletSseConnectionManager.class);
        LocalWalletSsePublisher publisher = new LocalWalletSsePublisher(connectionManager);
        WalletBalanceChangedEvent event = new WalletBalanceChangedEvent(
                7, new WalletBalanceResponse(20_000L, 3_000L, 17_000L), 42L,
                Instant.parse("2026-08-12T00:00:00Z")
        );

        publisher.publish(event);

        ArgumentCaptor<WalletSsePayload> payloadCaptor = ArgumentCaptor.forClass(WalletSsePayload.class);
        verify(connectionManager).push(eq(7), payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().walletVersion()).isEqualTo(42L);
    }
}
