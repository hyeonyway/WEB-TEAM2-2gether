package com.dbidding.auction.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionSseEventListener {
    private final AuctionSseConnectionManager connectionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuctionStreamEvent(AuctionStreamPayload payload) {
        connectionManager.broadcast(payload);
    }
}
