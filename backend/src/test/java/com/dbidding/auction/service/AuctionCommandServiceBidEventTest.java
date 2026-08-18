package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbidding.auction.bid.dto.AuctionCloseData;
import com.dbidding.auction.bid.dto.BidCommand;
import com.dbidding.auction.bid.dto.BidEventData;
import com.dbidding.auction.bid.dto.BidExecutionResult;
import com.dbidding.auction.bid.BidExecutor;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.card.service.CardService;
import com.dbidding.order.service.OrderService;
import com.dbidding.upload.adapter.AuctionImageUploadAdapter;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.service.WalletService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * #281 — {@code BidExecutor}가 더 이상 이벤트를 발행하지 않으므로, {@code participate()}가
 * result로부터 이벤트를 올바르게 조립·발행하는지 검증한다(상회입찰/최초입찰/즉시낙찰/
 * 멱등재생 4가지 케이스).
 */
@ExtendWith(MockitoExtension.class)
class AuctionCommandServiceBidEventTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private AuctionImageUploadAdapter imageUploadAdapter;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
    @Mock
    private AuctionStreamPublisher auctionStreamPublisher;
    @Mock
    private CardService cardService;
    @Mock
    private OrderService orderService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private BidExecutor bidExecutor;

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);
    private AuctionCommandService auctionCommandService;

    @BeforeEach
    void setUp() {
        auctionCommandService = new AuctionCommandService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletService,
                imageUploadAdapter,
                auctionEventPublisher,
                auctionStreamPublisher,
                cardService,
                orderService,
                clock,
                eventPublisher,
                new AuctionMetrics(new SimpleMeterRegistry()),
                bidExecutor
        );
    }

    @Test
    void 상회입찰이면_BidPlacedEvent를_조립해서_로컬과_스트림_양쪽에_발행한다() {
        BidExecutionResult outcome = outcomeWith(new BidEventData(
                1, 9, 20L, 40_000L, 1_000L, AuctionStatus.OPEN, null
        ));
        when(bidExecutor.execute(any(BidCommand.class))).thenReturn(outcome);

        auctionCommandService.participate(2, 1, new BidCreateRequest(43_000L), "bid-key");

        verify(auctionEventPublisher).publishBidPlaced(argThat((BidPlacedEvent event) ->
                event.auctionId().equals(1)
                        && event.bidderId().equals(2)
                        && event.previousBidderId().equals(9)
                        && event.previousBidId().equals(20L)
                        && event.itemId().equals(1)
                        && event.startPrice().equals(40_000L)
                        && event.bidIncrement().equals(1_000L)
                        && event.currentPrice().equals(43_000L)
        ));
        verify(auctionStreamPublisher).publish(any());
        verify(auctionEventPublisher, never()).publishClosed(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 최초입찰이면_previousBidderId가_null인_이벤트를_발행한다() {
        BidExecutionResult outcome = outcomeWith(new BidEventData(
                1, null, null, 40_000L, 1_000L, AuctionStatus.OPEN, null
        ));
        when(bidExecutor.execute(any(BidCommand.class))).thenReturn(outcome);

        auctionCommandService.participate(2, 1, new BidCreateRequest(43_000L), "bid-key");

        verify(auctionEventPublisher).publishBidPlaced(argThat((BidPlacedEvent event) ->
                event.previousBidderId() == null && event.previousBidId() == null
        ));
        verify(auctionStreamPublisher).publish(any());
    }

    @Test
    void 즉시낙찰이면_AuctionClosedEvent도_로컬과_스트림_양쪽에_발행한다() {
        AuctionCloseData closeData = new AuctionCloseData(1, "리자몽", "10", "JP", "/thumb.png", 5);
        BidExecutionResult outcome = outcomeWith(new BidEventData(
                1, null, null, 40_000L, 1_000L, AuctionStatus.ENDED, closeData
        ));
        when(bidExecutor.execute(any(BidCommand.class))).thenReturn(outcome);

        auctionCommandService.participate(2, 1, new BidCreateRequest(100_000L), "buy-now-key");

        verify(auctionEventPublisher).publishClosed(argThat((AuctionClosedEvent event) ->
                event.auctionId().equals(1)
                        && event.winnerId().equals(2)
                        && event.sellerId().equals(5)
                        && event.cardName().equals("리자몽")
                        && event.status() == AuctionStatus.ENDED
        ));
        verify(auctionStreamPublisher, org.mockito.Mockito.times(2)).publish(any());
    }

    @Test
    void 멱등_재생이면_아무_이벤트도_발행하지_않는다() {
        BidExecutionResult outcome = outcomeWith(null);
        when(bidExecutor.execute(any(BidCommand.class))).thenReturn(outcome);

        auctionCommandService.participate(2, 1, new BidCreateRequest(43_000L), "bid-key");

        verifyNoInteractions(auctionEventPublisher, auctionStreamPublisher);
        verify(eventPublisher, never()).publishEvent(any());
    }

    private BidExecutionResult outcomeWith(BidEventData eventData) {
        BidResponses.BidResult result = new BidResponses.BidResult(
                new BidResponses.BidDetail(10L, 43_000L, BidStatus.LEADING, clock.instant()),
                new BidResponses.AuctionSnapshot(1, 43_000L, 44_000L, 3, clock.instant().plusSeconds(3600)),
                new BidResponses.WalletSummary(957_000L, 43_000L)
        );
        return new BidExecutionResult(result, eventData);
    }
}
