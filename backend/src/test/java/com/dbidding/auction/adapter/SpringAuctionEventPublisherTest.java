package com.dbidding.auction.adapter;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.notification.event.AuctionCreatedEvent;
import com.dbidding.sse.auction.payload.AuctionClosedPayload;
import com.dbidding.sse.auction.payload.AuctionCreatedPayload;
import com.dbidding.sse.auction.payload.AuctionPayloadStatus;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
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
    void 생성_이벤트를_알림과_SSE_리스너_계약으로_각각_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 3, 10, 0);
        LocalDateTime closeTime = occurredAt.plusHours(12);

        publisher.publishOpened(new AuctionOpenedEvent(
                1, 10, "리자몽", "10", "JP", "/cards/charizard.png", 2,
                42_000L, 42_000L, 1_000L, 0, closeTime, AuctionStatus.OPEN, 1L, occurredAt
        ));

        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof AuctionCreatedEvent notification
                        && notification.auctionId().equals(1)
                        && notification.cardId().equals(10)
                        && notification.cardName().equals("리자몽")
                        && notification.sellerId().equals(2)));
        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof AuctionCreatedPayload payload
                        && payload.auctionId().equals(1)
                        && payload.cardId().equals(10)
                        && payload.currentPrice().equals(42_000L)
                        && payload.status() == AuctionPayloadStatus.OPEN));
    }

    @Test
    void 입찰_이벤트는_SSE_리스너_계약으로만_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 3, 11, 0);
        LocalDateTime closeTime = occurredAt.plusMinutes(5);

        publisher.publishBidPlaced(new BidPlacedEvent(
                1, 10, 3, 2, 42_000L, 45_000L, 1_000L, 2,
                closeTime, AuctionStatus.ENDING, 2L, occurredAt
        ));

        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof BidPlacedPayload payload
                        && payload.auctionId().equals(1)
                        && payload.bidderId().equals(3)
                        && payload.previousBidderId().equals(2)
                        && payload.currentPrice().equals(45_000L)
                        && payload.status() == AuctionPayloadStatus.ENDING));
        verifyNoMoreInteractions(applicationEventPublisher);
    }

    @Test
    void 최초_입찰은_상회_입찰_알림을_발행하지_않는다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 8, 3, 11, 0);

        publisher.publishBidPlaced(new BidPlacedEvent(
                1, 10, 3, null, 42_000L, 43_000L, 1_000L, 1,
                occurredAt.plusHours(1), AuctionStatus.OPEN, 1L, occurredAt
        ));

        verify(applicationEventPublisher, never())
                .publishEvent(isA(com.dbidding.notification.event.BidOutbidEvent.class));
    }

    @Test
    void 낙찰_이벤트를_알림과_SSE_리스너_계약으로_각각_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime closedAt = LocalDateTime.of(2026, 8, 3, 12, 0);
        AuctionClosedEvent event = closedEvent(
                3,
                45_000L,
                45_000L,
                AuctionStatus.ENDED,
                closedAt
        );

        publisher.publishClosed(event);

        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof com.dbidding.notification.event.AuctionClosedEvent notification
                        && notification.auctionId().equals(1)
                        && notification.cardId().equals(10)
                        && notification.cardName().equals("리자몽")
                        && notification.winnerId().equals(3)
                        && notification.sellerId().equals(2)
                        && notification.finalPrice().equals(45_000L)));
        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof AuctionClosedPayload payload
                        && payload.auctionId().equals(1)
                        && payload.cardId().equals(10)
                        && payload.winnerId().equals(3)
                        && payload.finalPrice().equals(45_000L)
                        && payload.status() == AuctionPayloadStatus.ENDED));
    }

    @Test
    void 유찰_이벤트는_알림의_낙찰가를_null로_SSE의_현재가를_최종가로_발행한다() {
        SpringAuctionEventPublisher publisher = new SpringAuctionEventPublisher(applicationEventPublisher);
        LocalDateTime closedAt = LocalDateTime.of(2026, 8, 3, 12, 0);
        AuctionClosedEvent event = closedEvent(
                null,
                42_000L,
                null,
                AuctionStatus.FAILED,
                closedAt
        );

        publisher.publishClosed(event);

        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof com.dbidding.notification.event.AuctionClosedEvent notification
                        && notification.winnerId() == null
                        && notification.finalPrice() == null));
        verify(applicationEventPublisher).publishEvent(argThat((Object published) ->
                published instanceof AuctionClosedPayload payload
                        && payload.winnerId() == null
                        && payload.finalPrice().equals(42_000L)
                        && payload.status() == AuctionPayloadStatus.FAILED));
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
