package com.dbidding.sse.auction;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionCreatedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
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
    public void onAuctionCreated(AuctionCreatedEvent event) {
        connectionManager.broadcast(AuctionSseEvent.AuctionCreated.from(event));
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onBidPlaced(BidPlacedEvent event) {
        connectionManager.broadcast(AuctionSseEvent.BidPlaced.from(event));
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true
    )
    public void onAuctionClosed(AuctionClosedEvent event) {
        connectionManager.broadcast(AuctionSseEvent.AuctionClosed.from(event));
    }
}
