package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.card.service.CardService;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
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
        lenient().when(cardService.getCardSnapshot(1)).thenReturn(new com.dbidding.card.dto.CardResponses.CardSnapshot(1, "카드", "세트", "10", "JP", null));
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

    @ParameterizedTest
    @ValueSource(longs = {100_000L, 110_000L})
    void 즉시구매가_이상_입찰은_즉시구매가로_낙찰되고_경매를_종료한다(long requestedPrice) {
        Auction auction = auction(1);
        ReflectionTestUtils.setField(auction, "currentPrice", 95_000L);
        ReflectionTestUtils.setField(auction, "bidPriceUnit", 10_000L);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING)).thenReturn(Optional.empty());
        when(walletService.hold(2, 1, 100_000L)).thenReturn(new WalletBalanceResponse(1_000_000L, 100_000L, 900_000L));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            ReflectionTestUtils.setField(bid, "id", 10L);
            when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING)).thenReturn(Optional.of(bid));
            return bid;
        });

        var response = auctionService.participate(2, 1, new BidCreateRequest(requestedPrice), "buy-now-key");

        assertThat(response.bid().amount()).isEqualTo(100_000L);
        assertThat(auction.getStatus()).isEqualTo(AuctionStatus.ENDED);
        verify(walletService).capture(2, 1, 100_000L);
        verify(auctionEventPublisher).publishClosed(any(AuctionClosedEvent.class));
    }

    @Test
    void 기존_최고가_입찰자가_있어도_즉시구매하면_기존_예치금을_해제하고_구매자_예치금을_확정한다() {
        Auction auction = auction(1);
        Bid previous = Bid.leading(3, auction, 90_000L, clock.instant(), "previous", "hash");
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING)).thenReturn(Optional.of(previous));
        when(walletService.release(3, 1)).thenReturn(new WalletBalanceResponse(1_000_000L, 0L, 1_000_000L));
        when(walletService.hold(2, 1, 100_000L)).thenReturn(new WalletBalanceResponse(1_000_000L, 100_000L, 900_000L));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING)).thenReturn(Optional.of(bid));
            return bid;
        });

        auctionService.participate(2, 1, new BidCreateRequest(100_000L), "buy-now-key");

        verify(walletService).release(3, 1);
        verify(walletService).capture(2, 1, 100_000L);
    }

    @Test
    void 서로_다른_두_지갑을_처리할_때_사용자_ID_오름차순으로_락을_획득한다() {
        Auction auction = auction(1);
        Bid previous = Bid.leading(3, auction, 90_000L, clock.instant(), "previous", "hash");
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(previous));
        when(walletService.hold(2, 1, 91_000L))
                .thenReturn(new WalletBalanceResponse(1_000_000L, 91_000L, 909_000L));
        when(walletService.release(3, 1)).thenReturn(new WalletBalanceResponse(1_000_000L, 0L, 1_000_000L));
        when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));

        auctionService.participate(2, 1, new BidCreateRequest(91_000L), "bid-key");

        InOrder walletCalls = org.mockito.Mockito.inOrder(walletService);
        walletCalls.verify(walletService).hold(2, 1, 91_000L);
        walletCalls.verify(walletService).release(3, 1);
    }

    @Test
    void 동일_idempotency_key로_즉시구매를_재요청하면_기존_응답을_반환한다() {
        Auction auction = auction(1);
        ReflectionTestUtils.setField(auction, "status", AuctionStatus.ENDED);
        Bid bid = Bid.leading(2, auction, 100_000L, clock.instant(), "buy-now-key",
                "aab899678e19331286225b49ecf51ec86fe22057bab2b7d2e2fb06339c655c54");
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByBidderIdAndAuctionIdAndIdempotencyKey(2, 1, "buy-now-key")).thenReturn(Optional.of(bid));
        when(walletService.getBalance(2)).thenReturn(new WalletBalanceResponse(1_000_000L, 0L, 900_000L));

        var response = auctionService.participate(2, 1, new BidCreateRequest(100_000L), "buy-now-key");

        assertThat(response.bid().amount()).isEqualTo(100_000L);
        verify(bidRepository, never()).save(any(Bid.class));
        verify(walletService, never()).capture(any(), any(), any(Long.class));
    }

    @Test
    void 마감_임박_입찰로_종료_시간이_연장되면_스케줄_변경_이벤트를_발행한다() {
        Auction auction = auction(1);
        Instant previousCloseTime = clock.instant().plus(Duration.ofMinutes(4));
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
                        && changed.closeTime().equals(previousCloseTime.plus(Duration.ofMinutes(5)))
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
                .openTime(clock.instant().minus(Duration.ofHours(1)))
                .estimatedCloseTime(clock.instant().plus(Duration.ofHours(1)))
                .closeTime(clock.instant().plus(Duration.ofHours(1)))
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        return auction;
    }
}
