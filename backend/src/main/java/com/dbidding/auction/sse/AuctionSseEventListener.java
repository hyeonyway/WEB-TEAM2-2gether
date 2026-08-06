package com.dbidding.auction.sse;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class AuctionSseEventListener {
    private final AuctionSseConnectionManager connectionManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuctionOpened(AuctionOpenedEvent event) {
        connectionManager.broadcast(AuctionStreamPayload.created(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onBidPlaced(BidPlacedEvent event) {
        connectionManager.broadcast(AuctionStreamPayload.bidPlaced(event));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAuctionClosed(AuctionClosedEvent event) {
        connectionManager.broadcast(AuctionStreamPayload.closed(event));
    }
}
