package com.dbidding.wallet.sse;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import java.time.Instant;

/** 지갑 상태 전이가 승인된 직후 browser SSE로 전달할 내부 이벤트다. */
public record WalletBalanceChangedEvent(
        Integer userId,
        WalletBalanceResponse balance,
        long walletVersion,
        Instant occurredAt
) {
}
