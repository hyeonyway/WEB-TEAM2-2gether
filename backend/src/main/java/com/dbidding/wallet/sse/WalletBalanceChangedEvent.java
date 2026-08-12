package com.dbidding.wallet.sse;

import com.dbidding.wallet.dto.WalletBalanceResponse;
import java.time.Instant;

/** 지갑 DB 상태가 커밋된 뒤 browser SSE로 전달할 내부 이벤트다. */
public record WalletBalanceChangedEvent(
        Integer userId,
        WalletBalanceResponse balance,
        long walletVersion,
        Instant occurredAt
) {
}
