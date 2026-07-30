package com.dbidding.sse.auction;

import com.dbidding.sse.auction.payload.AuctionClosedPayload;
import com.dbidding.sse.auction.payload.AuctionCreatedPayload;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionSseEventListener {
    private final AuctionSseConnectionManager connectionManager;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onAuctionCreated(AuctionCreatedPayload payload) {
        connectionManager.broadcast(payload);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onBidPlaced(BidPlacedPayload payload) {
        connectionManager.broadcast(payload);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onAuctionClosed(AuctionClosedPayload payload) {
        connectionManager.broadcast(payload);
    }
}
