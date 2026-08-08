package com.dbidding.auction.adapter;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.sse.AuctionStreamEventType;
import com.dbidding.auction.sse.AuctionStreamPayload;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SpringAuctionEventPublisherTest {
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void 생성_이벤트를_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        Instant occurredAt = Instant.parse("2026-08-03T10:00:00Z");
        Instant closeTime = occurredAt.plus(Duration.ofHours(12));
        AuctionOpenedEvent event = new AuctionOpenedEvent(
                1, 10, "리자몽", "10", "JP", "/cards/charizard.png", 2,
                42_000L, 42_000L, 1_000L, 0, closeTime, AuctionStatus.OPEN, occurredAt
        );

        publisher.publishOpened(event);

        verify(applicationEventPublisher).publishEvent(event);
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    void 입찰_이벤트를_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        Instant occurredAt = Instant.parse("2026-08-03T11:00:00Z");
        Instant closeTime = occurredAt.plus(Duration.ofMinutes(5));
        BidPlacedEvent event = new BidPlacedEvent(
                1, 10, 3, 2, 20L, 42_000L, 45_000L, 1_000L, 2,
                closeTime, AuctionStatus.ENDING, occurredAt
        );

        publisher.publishBidPlaced(event);

        verify(applicationEventPublisher).publishEvent(event);
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    void 종료_이벤트를_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        Instant closedAt = Instant.parse("2026-08-03T12:00:00Z");
        AuctionClosedEvent event = closedEvent(3, 45_000L, 45_000L, AuctionStatus.ENDED, closedAt);

        publisher.publishClosed(event);

        verify(applicationEventPublisher).publishEvent(event);
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    private AuctionClosedEvent closedEvent(
            Integer winnerId,
            Long currentPrice,
            Long winningPrice,
            AuctionStatus status,
            Instant closedAt
    ) {
        return new AuctionClosedEvent(
                1,
                10,
                "리자몽",
                "10",
                "JP",
                "/cards/charizard.png",
                winnerId,
                2,
                42_000L,
                currentPrice,
                winningPrice,
                1_000L,
                3,
                closedAt,
                status,
                closedAt
        );
    }
}
