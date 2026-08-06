package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.card.service.CardService;
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
class AuctionServiceBidTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private CardService cardService;
    @Mock
    private AuctionEventPublisher auctionEventPublisher;
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
                clock,
                eventPublisher,
                new AuctionMetrics(meterRegistry)
        );
    }

    @Test
    void 판매자는_자신의_경매에_입찰할_수_없다() {
        Auction auction = auction(1);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));

        assertThatThrownBy(() -> auctionService.participate(1, 1, new BidCreateRequest(43_000L), "bid-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);

        assertThat(auction.getCurrentPrice()).isEqualTo(42_000L);
        assertThat(auction.getBidCount()).isZero();
        verify(bidRepository, never())
                .findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(any(), any());
        verify(walletService, never()).hold(any(), any(), any(Long.class));
        verify(bidRepository, never()).save(any(Bid.class));
        verifyNoInteractions(auctionEventPublisher);
        assertThat(meterRegistry.get("dbidding.auction.lock.wait")
                .tag("operation", "bid")
                .timer()
                .count()).isEqualTo(1);
    }

    @Test
    void 판매자가_아닌_사용자는_입찰할_수_있다() {
        Auction auction = auction(1);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());
        when(walletService.hold(2, 1, 43_000L))
                .thenReturn(new WalletBalanceResponse(1_000_000L, 43_000L, 957_000L));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            ReflectionTestUtils.setField(bid, "id", 10L);
            return bid;
        });

        var response = auctionService.participate(2, 1, new BidCreateRequest(43_000L), "bid-key");

        assertThat(response.bid().id()).isEqualTo(10L);
        assertThat(response.bid().amount()).isEqualTo(43_000L);
        assertThat(response.auction().currentPrice()).isEqualTo(43_000L);
        assertThat(auction.getCurrentPrice()).isEqualTo(43_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        verify(walletService).hold(2, 1, 43_000L);
        verify(auctionEventPublisher).publishBidPlaced(any(BidPlacedEvent.class));
    }

    @Test
    void 마감_임박_입찰로_종료_시간이_연장되면_스케줄_변경_이벤트를_발행한다() {
        Auction auction = auction(1);
        LocalDateTime previousCloseTime = LocalDateTime.now(clock).plusMinutes(4);
        ReflectionTestUtils.setField(auction, "closeTime", previousCloseTime);
        ReflectionTestUtils.setField(auction, "estimatedCloseTime", previousCloseTime);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());
        when(walletService.hold(2, 1, 43_000L))
                .thenReturn(new WalletBalanceResponse(1_000_000L, 43_000L, 957_000L));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auctionService.participate(2, 1, new BidCreateRequest(43_000L), "bid-key");

        verify(eventPublisher).publishEvent(argThat((Object event) ->
                event instanceof AuctionCloseScheduleChangedEvent changed
                        && changed.auctionId().equals(1)
                        && changed.closeTime().equals(previousCloseTime.plusMinutes(5))
                        && changed.reason().equals("close_time_extended")));
    }

    private Auction auction(Integer sellerId) {
        Auction auction = Auction.builder()
                .sellerId(sellerId)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(LocalDateTime.now(clock).minusHours(1))
                .estimatedCloseTime(LocalDateTime.now(clock).plusHours(1))
                .closeTime(LocalDateTime.now(clock).plusHours(1))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        return auction;
    }
}
