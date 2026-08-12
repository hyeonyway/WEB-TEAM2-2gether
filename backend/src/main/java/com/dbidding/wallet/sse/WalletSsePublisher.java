package com.dbidding.wallet.sse;

public interface WalletSsePublisher {
    String CHANNEL = "wallet:sse";
    void publish(WalletBalanceChangedEvent event);
}
