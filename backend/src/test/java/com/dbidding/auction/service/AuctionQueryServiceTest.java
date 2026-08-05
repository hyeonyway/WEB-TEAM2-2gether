package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionCursorRevision;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionQueryServiceTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletPort walletPort;
    @Mock
    private AuctionCardPort auctionCardPort;

    private AuctionQueryService auctionQueryService;
    private final AuctionCursorCodec cursorCodec = new AuctionCursorCodec();

    @BeforeEach
    void setUp() {
        auctionQueryService = new AuctionQueryService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletPort,
                auctionCardPort,
                cursorCodec
        );
    }

    @Test
    void 경매_목록은_size보다_하나_더_조회하고_마지막_항목으로_다음_cursor를_만든다() {
        Auction first = auction(3, AuctionStatus.OPEN, 50_000L, 7);
        Auction second = auction(2, AuctionStatus.OPEN, 45_000L, 5);
        Auction extra = auction(1, AuctionStatus.OPEN, 40_000L, 3);
        when(auctionRepository.findCursorRevision()).thenReturn(new AuctionCursorRevision(3L, 8L));
        when(auctionRepository.searchByCursor(
                eq(""), eq(null), eq(List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)),
                eq(AuctionSort.BID_COUNT.name()), eq(null), eq(null), eq(null), eq(null), eq(null), eq(true), any(LocalDateTime.class),
                eq(PageRequest.of(0, 3))
        )).thenReturn(List.of(first, second, extra));
        when(auctionCardPort.getCardSnapshots(List.of(1))).thenReturn(Map.of(
                1, new AuctionCardPort.CardSnapshot(1, "Mock Card", "Mock Set", "10", "JP", "/card.png")
        ));
        when(auctionImageRepository.findByAuctionIdInOrderById(List.of(3, 2)))
                .thenReturn(List.of());

        var response = auctionQueryService.search(
                null,
                new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 2)
        );

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(3, 2);
        assertThat(response.hasNext()).isTrue();
        assertThat(cursorCodec.decode(response.nextCursor(), AuctionSort.BID_COUNT))
                .isEqualTo(new AuctionCursor(AuctionSort.BID_COUNT, 5L, null, 2, 3L, 8L));
    }

    @Test
    void 변동_정렬_cursor의_스냅샷이_달라지면_목록_재시작을_요구한다() {
        AuctionCursor staleCursor = new AuctionCursor(AuctionSort.PRICE_HIGH, 45_000L, null, 2, 3L, 8L);
        when(auctionRepository.findCursorRevision()).thenReturn(new AuctionCursorRevision(3L, 9L));

        var request = new AuctionSearchRequest(
                "", null, AuctionSort.PRICE_HIGH, null, cursorCodec.encode(staleCursor), 2
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> auctionQueryService.search(null, request))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
                .isEqualTo(409);
    }

    @Test
    void 상승률순_cursor는_마지막_경매의_상승률_정렬값을_사용한다() {
        Auction first = auction(2, AuctionStatus.OPEN, 15_000L, 1);
        Auction extra = auction(1, AuctionStatus.OPEN, 12_000L, 1);
        ReflectionTestUtils.setField(first, "changeRateBasisPoints", 5_000L);
        when(auctionRepository.findCursorRevision()).thenReturn(new AuctionCursorRevision(2L, 2L));
        when(auctionRepository.searchByCursor(
                eq(""), eq(null), eq(List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)),
                eq(AuctionSort.CHANGE_HIGH.name()), eq(null), eq(null), eq(null), eq(null), eq(null), eq(true),
                any(LocalDateTime.class), eq(PageRequest.of(0, 2))
        )).thenReturn(List.of(first, extra));
        when(auctionCardPort.getCardSnapshots(List.of(1))).thenReturn(Map.of(
                1, new AuctionCardPort.CardSnapshot(1, "Mock Card", "Mock Set", "10", "JP", "/card.png")
        ));
        when(auctionImageRepository.findByAuctionIdInOrderById(List.of(2))).thenReturn(List.of());

        var response = auctionQueryService.search(
                null,
                new AuctionSearchRequest("", null, AuctionSort.CHANGE_HIGH, null, null, 1)
        );

        assertThat(cursorCodec.decode(response.nextCursor(), AuctionSort.CHANGE_HIGH).value()).isEqualTo(5_000L);
    }

    @Test
    void 종료된_경매의_낙찰_입찰을_최고_입찰로_표시한다() {
        Auction auction = auction(AuctionStatus.ENDED);
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.WON);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(1, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(winningBid)));
        when(bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                1,
                List.of(BidStatus.LEADING, BidStatus.WON)
        )).thenReturn(Optional.of(winningBid));

        var response = auctionQueryService.getBids(1, new PageRequestDto(0, 20));

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().isHighest()).isTrue();
    }

    @Test
    void 낙찰자의_입찰_상태를_앞선_입찰로_표시한다() {
        Auction auction = auction(AuctionStatus.ENDED);
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.WON);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(walletPort.getWallet(3)).thenReturn(new WalletPort.WalletSnapshot(100_000L, 45_000L));
        when(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDesc(1, 3))
                .thenReturn(Optional.of(winningBid));
        when(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(1, PageRequest.of(0, 5)))
                .thenReturn(new PageImpl<>(List.of(winningBid)));
        when(bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                1,
                List.of(BidStatus.LEADING, BidStatus.WON)
        )).thenReturn(Optional.of(winningBid));

        var response = auctionQueryService.getBidContext(3, 1);

        assertThat(response.myBidStatus()).isEqualTo(MyBidStatus.LEADING);
    }

    @Test
    void 상세_조회는_실제_마감_시각을_반환한다() {
        Auction auction = auction(AuctionStatus.ENDED);
        LocalDateTime actualCloseTime = LocalDateTime.now().minusSeconds(30);
        ReflectionTestUtils.setField(auction, "closeTime", actualCloseTime);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1,
                "Mock Card",
                "Mock Set",
                "10",
                "JP",
                "/mock/card.png"
        ));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of());
        when(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDesc(1, 3))
                .thenReturn(Optional.empty());

        var response = auctionQueryService.getDetail(3, 1);

        assertThat(response.endsAt()).isEqualTo(actualCloseTime);
    }

    @Test
    void 비로그인_상세_조회는_내_입찰을_조회하지_않는다() {
        Auction auction = auction(AuctionStatus.OPEN);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1, "Mock Card", "Mock Set", "10", "JP", "/mock/card.png"
        ));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of());

        var response = auctionQueryService.getDetail(null, 1);

        assertThat(response.myBidStatus()).isEqualTo(MyBidStatus.NONE);
        assertThat(response.myBidAmount()).isNull();
        verifyNoInteractions(bidRepository);
    }

    @Test
    void 상세_조회는_카드_원본_이미지와_판매자_업로드_사진을_분리한다() {
        Auction auction = auction(AuctionStatus.OPEN);
        AuctionImage first = new AuctionImage(auction, "/uploads/front.png");
        AuctionImage second = new AuctionImage(auction, "/uploads/back.png");
        ReflectionTestUtils.setField(first, "id", 11);
        ReflectionTestUtils.setField(second, "id", 12);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(auctionCardPort.getCardSnapshot(1)).thenReturn(new AuctionCardPort.CardSnapshot(
                1, "Mock Card", "Mock Set", "10", "JP", "/cards/original.png"
        ));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of(first, second));

        var response = auctionQueryService.getDetail(null, 1);

        assertThat(response.card().thumbnailUrl()).isEqualTo("/cards/original.png");
        assertThat(response.photos()).extracting(photo -> photo.url())
                .containsExactly("/uploads/front.png", "/uploads/back.png");
    }

    private Auction auction(AuctionStatus status) {
        return auction(1, status, 45_000L, 0);
    }

    private Auction auction(Integer id, AuctionStatus status, Long currentPrice, Integer bidCount) {
        LocalDateTime closeTime = LocalDateTime.now().minusMinutes(1);
        Auction auction = Auction.builder()
                .sellerId(2)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minusHours(2))
                .estimatedCloseTime(closeTime)
                .closeTime(closeTime)
                .bidPriceUnit(1_000L)
                .hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", id);
        ReflectionTestUtils.setField(auction, "status", status);
        ReflectionTestUtils.setField(auction, "currentPrice", currentPrice);
        ReflectionTestUtils.setField(auction, "bidCount", bidCount);
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, Long bidPrice, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, bidPrice, LocalDateTime.now().minusMinutes(5), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }
}
