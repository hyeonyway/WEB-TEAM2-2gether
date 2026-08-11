package com.dbidding.auction.sse;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.BidPlacedEvent;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalAuctionStreamPublisherTest {

    @Test
    void publish는_연결_관리자의_broadcast로_그대로_위임한다() {
        AuctionSseConnectionManager connectionManager = mock(AuctionSseConnectionManager.class);
        LocalAuctionStreamPublisher publisher = new LocalAuctionStreamPublisher(connectionManager);
        BidPlacedEvent event = new BidPlacedEvent(
                10, 1, 7, 5, 20L, 40_000L, 50_000L, 1_000L, 2,
                Instant.parse("2026-08-10T01:00:00Z"), AuctionStatus.OPEN, Instant.parse("2026-08-10T00:00:00Z"));
        AuctionStreamPayload payload = AuctionStreamPayload.bidPlaced(event);

        publisher.publish(payload);

        verify(connectionManager).broadcast(payload);
    }
}
