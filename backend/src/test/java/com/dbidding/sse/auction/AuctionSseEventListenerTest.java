package com.dbidding.sse.auction;

import static org.mockito.Mockito.verify;

import com.dbidding.sse.auction.payload.AuctionClosedPayload;
import com.dbidding.sse.auction.payload.AuctionCreatedPayload;
import com.dbidding.sse.auction.payload.AuctionPayloadStatus;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
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
    void 세_payload를_각각_공개_SSE로_전송한다() {
        AuctionSseEventListener listener = new AuctionSseEventListener(connectionManager);
        var created = new AuctionCreatedPayload(
                null, 10, 20, "리자몽", "10", "JP", "/card.png", 3,
                40_000L, 40_000L, 1_000L, 0, now.plusHours(1),
                AuctionPayloadStatus.OPEN, 1L, now
        );
        var bid = new BidPlacedPayload(
                null, 10, 20, "리자몽", "10", "JP", "/card.png", 3, 7, 5,
                40_000L, 50_000L, 50_000L, 1_000L, 1, now.plusHours(1),
                AuctionPayloadStatus.OPEN, 2L, now
        );
        var closed = new AuctionClosedPayload(
                null, 10, 20, "리자몽", "10", "JP", "/card.png", 7, 3,
                40_000L, 50_000L, 1_000L, 1, now, AuctionPayloadStatus.ENDED, 3L,
                now, now
        );

        listener.onAuctionCreated(created);
        listener.onBidPlaced(bid);
        listener.onAuctionClosed(closed);

        verify(connectionManager).broadcast(created);
        verify(connectionManager).broadcast(bid);
        verify(connectionManager).broadcast(closed);
    }
}
