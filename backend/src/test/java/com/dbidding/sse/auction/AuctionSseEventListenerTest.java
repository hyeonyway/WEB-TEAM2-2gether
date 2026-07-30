package com.dbidding.sse.auction;

import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionCreatedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuctionSseEventListenerTest {
    @Mock
    private AuctionSseConnectionManager connectionManager;
    private final LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);

    @Test
    void 세_도메인_이벤트를_각각_공개_SSE로_변환한다() {
        AuctionSseEventListener listener = new AuctionSseEventListener(connectionManager);
        var created = new AuctionCreatedEvent(
                10, 20, "리자몽", "10", "JP", "/card.png", 3,
                40_000L, 40_000L, 1_000L, 0, now.plusHours(1),
                AuctionStatus.OPEN, 1L, now
        );
        var bid = new BidPlacedEvent(
                10, 20, "리자몽", "10", "JP", "/card.png", 3, 7, 5,
                40_000L, 50_000L, 50_000L, 1_000L, 1, now.plusHours(1),
                AuctionStatus.OPEN, 2L, now
        );
        var closed = new AuctionClosedEvent(
                10, 20, "리자몽", "10", "JP", "/card.png", 7, 3,
                40_000L, 50_000L, 1_000L, 1, now, AuctionStatus.ENDED, 3L,
                now, now
        );

        listener.onAuctionCreated(created);
        listener.onBidPlaced(bid);
        listener.onAuctionClosed(closed);

        verify(connectionManager).broadcast(AuctionSseEvent.AuctionCreated.from(created));
        verify(connectionManager).broadcast(AuctionSseEvent.BidPlaced.from(bid));
        verify(connectionManager).broadcast(AuctionSseEvent.AuctionClosed.from(closed));
    }
}
