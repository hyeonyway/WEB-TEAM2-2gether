package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.auction.port.AuctionEventPort;
import com.dbidding.auction.port.CurrentUserPort;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    private CurrentUserPort currentUserPort;
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

    private AuctionService auctionService;

    @BeforeEach
    void setUp() {
        auctionService = new AuctionService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                currentUserPort,
                walletPort,
                imageUploadPort,
                auctionCardPort,
                auctionCardStatisticPort,
                auctionEventPort
        );
        lenient().when(auctionRepository.save(any(Auction.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(bidRepository.save(any(Bid.class))).thenAnswer(invocation -> invocation.getArgument(0));
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
    void 같은_사용자의_같은_요청은_idempotency_cache를_재사용한다() {
        when(currentUserPort.currentUser()).thenReturn(user(1));
        AuctionCreateRequest request = request("경매 A", 42_000L);

        var first = auctionService.create(request, "same-key");
        var second = auctionService.create(request, "same-key");

        assertThat(second).isEqualTo(first);
        verify(auctionRepository, times(1)).save(any(Auction.class));
        verify(auctionImageRepository, times(1)).saveAll(any());
        verify(auctionCardStatisticPort, times(1)).recordAuctionOpened(any(), any());
        verify(auctionEventPort, times(1)).publish(any());
    }

    @Test
    void 같은_사용자가_같은_key로_다른_요청을_보내면_충돌한다() {
        when(currentUserPort.currentUser()).thenReturn(user(1));
        auctionService.create(request("경매 A", 42_000L), "same-key");

        assertThatThrownBy(() -> auctionService.create(request("경매 B", 43_000L), "same-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
        verify(auctionRepository, times(1)).save(any(Auction.class));
    }

    @Test
    void 다른_사용자는_같은_idempotency_key를_공유하지_않는다() {
        when(currentUserPort.currentUser()).thenReturn(user(1), user(2));
        AuctionCreateRequest request = request("경매 A", 42_000L);

        auctionService.create(request, "same-key");
        auctionService.create(request, "same-key");

        verify(auctionRepository, times(2)).save(any(Auction.class));
    }

    @Test
    void idempotency_key가_비어_있으면_경매를_생성하지_않는다() {
        assertThatThrownBy(() -> auctionService.create(request("경매 A", 42_000L), " "))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        verify(currentUserPort, never()).currentUser();
        verify(auctionRepository, never()).save(any(Auction.class));
    }

    @Test
    void 판매자가_아니거나_제재된_사용자는_경매를_생성할_수_없다() {
        when(currentUserPort.currentUser()).thenReturn(
                new CurrentUserPort.CurrentUser(1, "buyer", false, false),
                new CurrentUserPort.CurrentUser(2, "restricted-seller", true, true)
        );

        assertThatThrownBy(() -> auctionService.create(request("경매 A", 42_000L), "buyer-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
        assertThatThrownBy(() -> auctionService.create(request("경매 B", 42_000L), "restricted-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(403);
        verify(auctionRepository, never()).save(any(Auction.class));
    }

    @Test
    void 이미지가_없거나_너무_많으면_경매를_생성할_수_없다() {
        when(currentUserPort.currentUser()).thenReturn(user(1), user(1));
        when(imageUploadPort.resolveImages(List.of("upl-1"))).thenReturn(List.of());

        assertThatThrownBy(() -> auctionService.create(request("경매 A", 42_000L), "empty-images"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        assertThatThrownBy(() -> auctionService.create(requestWithImageTokens(List.of(
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
        when(currentUserPort.currentUser()).thenReturn(user(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());

        var response = auctionService.participate(1, new BidCreateRequest(11_000L));

        assertThat(auction.getCurrentPrice()).isEqualTo(11_000L);
        assertThat(auction.getBidCount()).isEqualTo(1);
        assertThat(response.amount()).isEqualTo(11_000L);
        assertThat(response.isHighest()).isTrue();
        verify(walletPort).holdBidAmount(1, 1, 11_000L);
        verify(bidRepository).save(any(Bid.class));
        verify(auctionCardStatisticPort).recordBid(eq(1), any());
        verify(auctionEventPort).publish(any());
    }

    @Test
    void 새_입찰이_들어오면_이전_최고_입찰은_상회입찰_상태가_되고_동결금액이_해제된다() {
        Auction auction = auction(11_000L, 1_000L);
        Bid previousLeadingBid = Bid.leading(2, auction, 11_000L, LocalDateTime.now().minusMinutes(1));
        when(currentUserPort.currentUser()).thenReturn(user(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(previousLeadingBid));

        auctionService.participate(1, new BidCreateRequest(12_000L));

        assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.OUTBID);
        verify(walletPort).holdBidAmount(1, 1, 12_000L);
        verify(walletPort).releaseBidHold(2, 1);
        verify(auctionEventPort, times(2)).publish(any());
    }

    @Test
    void 같은_입찰자가_다시_최고가를_갱신하면_이전_동결금액을_해제하지_않는다() {
        Auction auction = auction(11_000L, 1_000L);
        Bid previousLeadingBid = Bid.leading(1, auction, 11_000L, LocalDateTime.now().minusMinutes(1));
        when(currentUserPort.currentUser()).thenReturn(user(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.of(previousLeadingBid));

        auctionService.participate(1, new BidCreateRequest(12_000L));

        assertThat(previousLeadingBid.getStatus()).isEqualTo(BidStatus.OUTBID);
        verify(walletPort).holdBidAmount(1, 1, 12_000L);
        verify(walletPort, never()).releaseBidHold(any(), any());
    }

    @Test
    void 최소_입찰가보다_낮으면_입찰할_수_없다() {
        Auction auction = auction(10_000L, 1_000L);
        when(currentUserPort.currentUser()).thenReturn(user(1));
        when(auctionRepository.findByIdForUpdate(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(1, BidStatus.LEADING))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> auctionService.participate(1, new BidCreateRequest(10_500L)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(400);
        verify(walletPort, never()).holdBidAmount(any(), any(), any(Long.class));
        verify(bidRepository, never()).save(any(Bid.class));
    }

    private CurrentUserPort.CurrentUser user(Integer userId) {
        return new CurrentUserPort.CurrentUser(userId, "seller-" + userId, true, false);
    }

    private AuctionCreateRequest request(String auctionName, Long startPrice) {
        return new AuctionCreateRequest(
                1,
                auctionName,
                "카드 상태 설명",
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
