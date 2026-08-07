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
import java.time.LocalDateTime;
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
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 3, 10, 0);
        LocalDateTime closeTime = occurredAt.plusHours(12);
        AuctionOpenedEvent event = new AuctionOpenedEvent(
                1, 10, "리자몽", "10", "JP", "/cards/charizard.png", 2,
                42_000L, 42_000L, 1_000L, 0, closeTime, AuctionStatus.OPEN, 1L, occurredAt
        );

        publisher.publishOpened(event);

        verify(applicationEventPublisher).publishEvent(event);
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    void 입찰_이벤트를_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 3, 11, 0);
        LocalDateTime closeTime = occurredAt.plusMinutes(5);
        BidPlacedEvent event = new BidPlacedEvent(
                1, 10, 3, 2, 20L, 42_000L, 45_000L, 1_000L, 2,
                closeTime, AuctionStatus.ENDING, 2L, occurredAt
        );

        publisher.publishBidPlaced(event);

        verify(applicationEventPublisher).publishEvent(event);
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    void 종료_이벤트를_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime closedAt = LocalDateTime.of(2026, 8, 3, 12, 0);
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
            LocalDateTime closedAt
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
                5L,
                closedAt
        );
    }
}
