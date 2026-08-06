package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.auction.port.AuctionEventPort;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
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
class AuctionServiceTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletPort walletPort;
    @Mock
    private ImageUploadPort imageUploadPort;
    @Mock
    private AuctionCardPort auctionCardPort;
    @Mock
    private AuctionCardStatisticPort auctionCardStatisticPort;
    @Mock
    private AuctionEventPort auctionEventPort;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private List<Auction> savedAuctions;
    private List<Bid> savedBids;
    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        savedAuctions = new ArrayList<>();
        savedBids = new ArrayList<>();
        auctionService = new AuctionService(
                new AuctionCommandService(
                        auctionRepository,
                        auctionImageRepository,
                        bidRepository,
                        walletPort,
                        imageUploadPort,
                        auctionCardPort,
                        auctionCardStatisticPort,
                        auctionEventPort,
                        clock,
                        eventPublisher,
                        new AuctionMetrics(new SimpleMeterRegistry())
                ),
                mock(AuctionQueryService.class),
                new AuctionMetrics(new SimpleMeterRegistry())
        );
        lenient().when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> {
            Auction auction = invocation.getArgument(0);
            savedAuctions.add(auction);
            return auction;
        });
        lenient().when(auctionRepository.findBySellerIdAndCreateIdempotencyKey(any(), anyString()))
                .thenAnswer(invocation -> {
                    Integer sellerId = invocation.getArgument(0);
                    String idempotencyKey = invocation.getArgument(1);
                    return savedAuctions.stream()
                            .filter(auction -> auction.getSellerId().equals(sellerId))
                            .filter(auction -> idempotencyKey.equals(auction.getCreateIdempotencyKey()))
                            .findFirst();
                });
        lenient().when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> {
            Bid bid = invocation.getArgument(0);
            savedBids.add(bid);
            return bid;
        });
        lenient().when(bidRepository.findFirstByBidderIdAndAuctionIdAndIdempotencyKey(any(), any(), anyString()))
                .thenAnswer(invocation -> {
                    Integer bidderId = invocation.getArgument(0);
                    Integer auctionId = invocation.getArgument(1);
                    String idempotencyKey = invocation.getArgument(2);
                    return savedBids.stream()
                            .filter(bid -> bid.getBidderId().equals(bidderId))
                            .filter(bid -> bid.getAuction().getId().equals(auctionId))
                            .filter(bid -> idempotencyKey.equals(bid.getIdempotencyKey()))
                            .findFirst();
                });
        lenient().when(walletPort.holdBidAmount(any(), any(), any(Long.class)))
                .thenReturn(new WalletPort.WalletSnapshot(900_000L, 100_000L));
        lenient().when(walletPort.getWallet(any()))
                .thenReturn(new WalletPort.WalletSnapshot(900_000L, 100_000L));
        lenient().when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1,
                "Mock Card",
                "Mock Set",
                "10",
                "JP",
                "/mock/card.png"
        ));
        lenient().when(imageUploadPort.resolveImages(List.of("upl-1"))).thenReturn(List.of(
                new ImageUploadPort.ResolvedImage("/mock/upl-1.png", 0, true)
        ));
    }

    @Test
    void 같은_사용자의_같은_요청은_저장된_idempotency_결과를_재사용한다() {
        AuctionCreateRequest request = request("경매 A", 42_000L);

        var first = auctionService.create(1, request, "same-key");
        var second = auctionService.create(1, request, "same-key");

        assertThat(second).isEqualTo(first);
        verify(auctionRepository, times(1)).save(any(Auction.class));
        verify(auctionImageRepository, times(1)).saveAll(any());
        verify(auctionCardStatisticPort, times(1)).recordAuctionOpened(any(), any());
        verify(auctionEventPort, times(1)).publishOpened(any(AuctionOpenedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(argThat((Object event) ->
                event instanceof AuctionCloseScheduleChangedEvent changed
                        && changed.reason().equals("auction_created")
                        && changed.closeTime().equals(LocalDateTime.now(clock).plusHours(12))));
    }

    @Test
    void 같은_사용자가_같은_key로_다른_요청을_보내면_충돌한다() {
        auctionService.create(1, request("경매 A", 42_000L), "same-key");

        assertThatThrownBy(() -> auctionService.create(1, request("경매 B", 43_000L), "same-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
        verify(auctionRepository, times(1)).save(any(Auction.class));
    }

    @Test
    void 다른_사용자는_같은_idempotency_key를_공유하지_않는다() {
        AuctionCreateRequest request = request("경매 A", 42_000L);

        auctionService.create(1, request, "same-key");
        auctionService.create(2, request, "same-key");

        verify(auctionRepository, times(2)).save(any(Auction.class));
    }

    @Test
    void idempotency_key가_비어_있으면_경매를_생성하지_않는다() {
        assertThatThrownBy(() -> auctionService.create(1, request("경매 A", 42_000L), " "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        verify(auctionRepository, never()).save(any(Auction.class));
    }

    @Test
    void 이미지가_없거나_너무_많으면_경매를_생성할_수_없다() {
        when(imageUploadPort.resolveImages(List.of("upl-1"))).thenReturn(List.of());

        assertThatThrownBy(() -> auctionService.create(1, request("경매 A", 42_000L), "empty-images"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        assertThatThrownBy(() -> auctionService.create(1, requestWithImageTokens(List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9"
        )), "too-many-images"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        verify(auctionRepository, never()).save(any(Auction.class));
    }

    @Test
    void 입찰하면_경매_현재가와_최고_입찰이_갱신된다() {
        Auction auction = auction(10_000L, 1_000L);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());

        var response = auctionService.participate(1, 1, new BidCreateRequest(11_000L), "bid-key");

        assertThat(auction.getCurrentPrice()).isEqualTo(11_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        assertThat(response.bid().amount()).isEqualTo(11_000L);
        assertThat(response.auction().currentPrice()).isEqualTo(11_000L);
        verify(walletPort).holdBidAmount(1, 1, 11_000L);
        verify(bidRepository).save(any(Bid.class));
        verify(auctionRepository).flush();
        verify(auctionCardStatisticPort).recordBid(eq(1), any());
        verify(auctionEventPort).publishBidPlaced(any(BidPlacedEvent.class));
    }

    @Test
    void 같은_사용자의_같은_입찰_요청은_저장된_idempotency_결과를_재사용한다() {
        Auction auction = auction(10_000L, 1_000L);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());

        var first = auctionService.participate(1, 1, new BidCreateRequest(11_000L), "same-bid-key");
        var second = auctionService.participate(1, 1, new BidCreateRequest(11_000L), "same-bid-key");

        assertThat(second).isEqualTo(first);
        assertThat(auction.getCurrentPrice()).isEqualTo(11_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        verify(auctionRepository, times(2)).findByIdForUpdate(1);
        verify(walletPort, times(1)).holdBidAmount(1, 1, 11_000L);
        verify(bidRepository, times(1)).save(any(Bid.class));
        verify(auctionEventPort, times(1)).publishBidPlaced(any(BidPlacedEvent.class));
    }

    @Test
    void 같은_사용자가_같은_입찰_key로_다른_금액을_보내면_충돌한다() {
        Auction auction = auction(10_000L, 1_000L);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());
        auctionService.participate(1, 1, new BidCreateRequest(11_000L), "same-bid-key");

        assertThatThrownBy(() -> auctionService.participate(1, 1, new BidCreateRequest(12_000L), "same-bid-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
        assertThat(auction.getBidCount()).isEqualTo(1);
        verify(bidRepository, times(1)).save(any(Bid.class));
    }

    @Test
    void 입찰_idempotency_key가_비어_있으면_입찰하지_않는다() {
        assertThatThrownBy(() -> auctionService.participate(1, 1, new BidCreateRequest(11_000L), " "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        verify(auctionRepository, never()).findByIdForUpdate(any());
        verify(bidRepository, never()).save(any(Bid.class));
    }

    @Test
    void 현재_입찰자_ID가_더_작으면_hold_후_release_순서로_지갑을_잠근다() {
        Auction auction = auction(11_000L, 1_000L);
        Bid previousLeadingBid = Bid.leading(3, auction, 11_000L, LocalDateTime.now().minusMinutes(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(previousLeadingBid));

        auctionService.participate(1, 1, new BidCreateRequest(12_000L), "bid-key");

        assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.OUTBID);
        var walletOrder = inOrder(walletPort);
        walletOrder.verify(walletPort).holdBidAmount(1, 1, 12_000L);
        walletOrder.verify(walletPort).releaseBidHold(3, 1);
        verify(auctionEventPort).publishBidPlaced(argThat(event ->
                event.previousBidderId().equals(3)
        ));
    }

    @Test
    void 이전_입찰자_ID가_더_작으면_release_후_hold_순서로_지갑을_잠근다() {
        Auction auction = auction(11_000L, 1_000L);
        Bid previousLeadingBid = Bid.leading(1, auction, 11_000L, LocalDateTime.now().minusMinutes(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(previousLeadingBid));

        auctionService.participate(3, 1, new BidCreateRequest(12_000L), "bid-key");

        assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.OUTBID);
        var walletOrder = inOrder(walletPort);
        walletOrder.verify(walletPort).releaseBidHold(1, 1);
        walletOrder.verify(walletPort).holdBidAmount(3, 1, 12_000L);
    }

    @Test
    void 같은_입찰자가_다시_최고가를_갱신하면_이전_동결금액을_해제하지_않는다() {
        Auction auction = auction(11_000L, 1_000L);
        Bid previousLeadingBid = Bid.leading(1, auction, 11_000L, LocalDateTime.now().minusMinutes(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(previousLeadingBid));

        auctionService.participate(1, 1, new BidCreateRequest(12_000L), "bid-key");

        assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.OUTBID);
        verify(walletPort).holdBidAmount(1, 1, 12_000L);
        verify(walletPort, never()).releaseBidHold(any(), any());
        verify(auctionEventPort).publishBidPlaced(any(BidPlacedEvent.class));
    }

    @Test
    void 최소_입찰가보다_낮으면_입찰할_수_없다() {
        Auction auction = auction(10_000L, 1_000L);
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.participate(1, 1, new BidCreateRequest(10_500L), "bid-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        verify(walletPort, never()).holdBidAmount(any(), any(), any(Long.class));
        verify(bidRepository, never()).save(any(Bid.class));
    }

    private AuctionCreateRequest request(String auctionName, Long startPrice) {
        return new AuctionCreateRequest(
                1,
                auctionName,
                "카드 상태 설명",
                null,
                null,
                List.of("upl-1"),
                startPrice,
                1_000L,
                100_000L,
                12,
                3_000L
        );
    }

    private AuctionCreateRequest requestWithImageTokens(List<String> imageUploadTokens) {
        return new AuctionCreateRequest(
                1,
                "경매 A",
                "카드 상태 설명",
                null,
                null,
                imageUploadTokens,
                42_000L,
                1_000L,
                100_000L,
                12,
                3_000L
        );
    }

    private Auction auction(Long startPrice, Long bidIncrement) {
        Auction auction = Auction.builder()
                .sellerId(2)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(startPrice)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(LocalDateTime.now().minusHours(1))
                .estimatedCloseTime(LocalDateTime.now().plusHours(1))
                .closeTime(LocalDateTime.now().plusHours(1))
                .bidPriceUnit(bidIncrement)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        return auction;
    }
}
