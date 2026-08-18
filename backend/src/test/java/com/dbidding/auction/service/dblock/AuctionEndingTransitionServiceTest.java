package com.dbidding.auction.service.dblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.EndingExtensionProvider;
import com.dbidding.auction.sse.AuctionStreamEventType;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class AuctionEndingTransitionServiceTest {
    private final AuctionRepository auctionRepository = mock(AuctionRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuctionStreamPublisher auctionStreamPublisher = mock(AuctionStreamPublisher.class);
    private final AuctionMetrics auctionMetrics = mock(AuctionMetrics.class);
    private final EndingExtensionProvider extensionProvider = mock(EndingExtensionProvider.class);
    private final AuctionEndingTransitionService service = new AuctionEndingTransitionService(
            auctionRepository, eventPublisher, auctionStreamPublisher, auctionMetrics, extensionProvider
    );

    @Test
    void 남은시간이_5분_이하인_OPEN_경매는_ENDING으로_전환하고_전환이벤트를_발행한다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Instant now = closeTime.minusSeconds(30);
        Auction auction = openAuction(1, closeTime);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(extensionProvider.next()).thenReturn(Duration.ofSeconds(90));

        boolean transitioned = service.transitionIfDue(1, now);

        assertThat(transitioned).isTrue();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDING);
        assertThat(auction.getEstimatedCloseTime()).isEqualTo(closeTime);
        assertThat(auction.getCloseTime()).isEqualTo(closeTime.plusSeconds(90));
        verify(auctionMetrics).recordEndingTransition();
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event -> event instanceof AuctionCloseScheduleChangedEvent changed
                && changed.auctionId().equals(1)
                && changed.closeTime().equals(closeTime.plusSeconds(90))
                && changed.reason().equals("ending_transition")));
        verify(auctionStreamPublisher).publish(argThat(payload ->
                payload.type() == AuctionStreamEventType.AUCTION_ENDING_STARTED
                        && payload.status() == AuctionStatus.ENDING
                        && payload.endsAt().equals(closeTime)
        ));
    }

    @Test
    void 아직_ENDING_진입시각_전인_OPEN_경매는_전환하지_않는다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = openAuction(1, closeTime);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        boolean transitioned = service.transitionIfDue(1, closeTime.minus(Duration.ofMinutes(6)));

        assertThat(transitioned).isFalse();
        verify(extensionProvider, never()).next();
        verify(auctionMetrics, never()).recordEndingTransition();
    }

    @Test
    void 이미_ENDING인_경매는_다시_전환하지_않는다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = openAuction(1, closeTime);
        auction.enterEnding(Duration.ofMinutes(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        boolean transitioned = service.transitionIfDue(1, closeTime.minusSeconds(30));

        assertThat(transitioned).isFalse();
        verify(extensionProvider, never()).next();
    }

    @Test
    void 실제_마감시각이_지난_OPEN_경매는_ENDING으로_되살리지_않는다() {
        Instant closeTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = openAuction(1, closeTime);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        boolean transitioned = service.transitionIfDue(1, closeTime.plusSeconds(1));

        assertThat(transitioned).isFalse();
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.OPEN);
        verify(extensionProvider, never()).next();
    }

    private Auction openAuction(Integer id, Instant closeTime) {
        Auction auction = Auction.builder()
                .sellerId(1).itemId(1).auctionName("경매 A").description("설명")
                .startPrice(10_000L).deliveryFee(0L)
                .openTime(closeTime.minus(Duration.ofDays(1)))
                .estimatedCloseTime(closeTime).closeTime(closeTime)
                .bidPriceUnit(1_000L).hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        return auction;
    }
}
