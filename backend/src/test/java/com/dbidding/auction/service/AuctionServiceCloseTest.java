package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionServiceCloseTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private AuctionCardStatisticPort auctionCardStatisticPort;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
    @Mock
    private CardService cardService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private AuctionCommandService auctionService;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        auctionService = new AuctionCommandService(
                auctionRepository,
                null,
                bidRepository,
                walletService,
                null,
                auctionEventPublisher,
                cardService,
                auctionCardStatisticPort,
                clock,
                eventPublisher,
                new AuctionMetrics(meterRegistry)
        );
        lenient().when(cardService.getCardSnapshot(1)).thenReturn(new CardSnapshot(
                1, "리자몽", "기본 세트", "10", "JP", "/cards/charizard.png"
        ));
    }

    @Test
    void 종료_시각이_지난_경매의_최고_입찰을_낙찰_처리한다() {
        Auction auction = auction(LocalDateTime.now(clock).minusMinutes(1));
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.LEADING);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(winningBid));

        var response = auctionService.closeAuction(1);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        assertThat(auction.getCurrentPrice()).isEqualTo(45_000L);
        assertThat(winningBid.getStatus()).isEqualTo(BidStatus.WON);
        assertThat(response.status()).isEqualTo(AuctionStatus.ENDED);
        assertThat(response.winnerId()).isEqualTo(3);
        assertThat(response.winningBidId()).isEqualTo(1L);
        assertThat(response.winningPrice()).isEqualTo(45_000L);
        verify(walletService).capture(3, 1, 45_000L);
        verify(auctionCardStatisticPort).recordAuctionCompleted(1, 45_000L, auction.getCloseTime());
        verify(auctionEventPublisher).publishClosed(argThat((AuctionClosedEvent closed) ->
                closed.auctionId().equals(1)
                && closed.itemId().equals(1)
                && closed.cardName().equals("리자몽")
                && closed.winnerId().equals(3)
                && closed.sellerId().equals(2)
                && closed.winningPrice().equals(45_000L)
                && closed.currentPrice().equals(45_000L)
                && closed.status() == AuctionStatus.ENDED));
        assertThat(meterRegistry.get("dbidding.auction.lock.wait")
                .tag("operation", "close")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void 입찰이_없는_종료_대상_경매는_거래_없이_종료한다() {
        Auction auction = auction(LocalDateTime.now(clock).minusMinutes(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());

        var response = auctionService.closeAuction(1);

        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.FAILED);
        assertThat(response.status()).isEqualTo(AuctionStatus.FAILED);
        assertThat(response.winnerId()).isNull();
        assertThat(response.winningBidId()).isNull();
        assertThat(response.winningPrice()).isNull();
        verify(walletService, never()).capture(any(), any(), any(Long.class));
        verify(auctionCardStatisticPort).recordAuctionClosedWithoutTrade(1, auction.getCloseTime());
        verify(auctionEventPublisher).publishClosed(argThat((AuctionClosedEvent closed) ->
                closed.auctionId().equals(1)
                && closed.cardName().equals("리자몽")
                && closed.winnerId() == null
                && closed.sellerId().equals(2)
                && closed.winningPrice() == null
                && closed.currentPrice().equals(42_000L)
                && closed.status() == AuctionStatus.FAILED));
    }

    @Test
    void 종료_시각이_지나지_않은_경매는_낙찰_처리하지_않는다() {
        Auction auction = auction(LocalDateTime.now(clock).plusMinutes(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> auctionService.closeAuction(1))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.OPEN);
        verify(walletService, never()).capture(any(), any(), any(Long.class));
        verify(auctionCardStatisticPort, never()).recordAuctionCompleted(any(), any(Long.class), any());
        verifyNoInteractions(auctionEventPublisher);
    }

    private Auction auction(LocalDateTime closeTime) {
        Auction auction = Auction.builder()
                .sellerId(2)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(LocalDateTime.now().minusHours(2))
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, Long bidPrice, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, bidPrice, LocalDateTime.now().minusMinutes(5), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }
}
