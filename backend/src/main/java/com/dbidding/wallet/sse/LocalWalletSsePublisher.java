package com.dbidding.wallet.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local-sse")
@RequiredArgsConstructor
public class LocalWalletSsePublisher implements WalletSsePublisher {
    private final WalletSseConnectionManager connectionManager;

    @Override
    public void publish(WalletBalanceChangedEvent event) {
        connectionManager.push(event.userId(), WalletSsePayload.from(event));
    }
}
