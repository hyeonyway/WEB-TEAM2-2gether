package com.dbidding.sse.auction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.sse.auction.AuctionSseTestAuctionReader.Snapshot;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class AuctionSseTestEventControllerTest {

    private final ApplicationEventPublisher eventPublisher =
            org.mockito.Mockito.mock(ApplicationEventPublisher.class);
    private final AuctionSseTestAuctionReader auctionReader =
            org.mockito.Mockito.mock(AuctionSseTestAuctionReader.class);
    private final AuctionSseTestEventController controller =
            new AuctionSseTestEventController(eventPublisher, auctionReader);

    @Test
    void publishesIncreasingBidsForRandomActiveAuction() {
        Snapshot snapshot = new Snapshot(
                11, 21, "피카츄", "10", "JP", "/pikachu.png", 3,
                100_000L, 110_000L, 1_000L, 10,
                LocalDateTime.now().plusHours(1), "OPEN", 4L, 8
        );
        when(auctionReader.findRandomActiveAuction()).thenReturn(Optional.of(snapshot));

        BidPlacedPayload first = controller.publishRandomBid();
        BidPlacedPayload second = controller.publishRandomBid();

        assertThat(first.auctionId()).isEqualTo(11);
        assertThat(first.currentPrice()).isEqualTo(111_000L);
        assertThat(first.previousBidderId()).isEqualTo(8);
        assertThat(second.currentPrice()).isEqualTo(112_000L);
        assertThat(second.bidCount()).isEqualTo(12);
        assertThat(second.auctionVersion()).isEqualTo(6L);
        assertThat(second.previousBidderId()).isEqualTo(first.bidderId());
        verify(eventPublisher).publishEvent(first);
        verify(eventPublisher).publishEvent(second);
    }
}
