package com.dbidding.wallet.sse;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/** browser에만 전달하는 지갑 snapshot. 내부 Stream ID·멱등키는 노출하지 않는다. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record WalletSsePayload(
        long walletVersion,
        long totalBalance,
        long frozenBalance,
        long availableBalance,
        Instant updatedAt
) {
    static WalletSsePayload from(WalletBalanceChangedEvent event) {
        WalletBalanceResponse balance = event.balance();
        return new WalletSsePayload(event.walletVersion(), balance.totalBalance(), balance.frozenBalance(),
                balance.availableBalance(), event.occurredAt());
    }
}
