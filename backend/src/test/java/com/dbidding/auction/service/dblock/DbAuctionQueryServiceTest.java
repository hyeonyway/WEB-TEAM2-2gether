package com.dbidding.auction.service.dblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
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

@ExtendWith(MockitoExtension.class)
class DbAuctionQueryServiceTest {

    @Mock private AuctionRepository auctionRepository;
    @Mock private AuctionImageRepository auctionImageRepository;
    @Mock private BidRepository bidRepository;
    @Mock private CardService cardService;
    @Mock private WalletService walletService;

    private final AuctionCursorCodec cursorCodec = new AuctionCursorCodec();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);
    private DbAuctionQueryService service;

    @BeforeEach
    void setUp() {
        service = new DbAuctionQueryService(
                auctionRepository, auctionImageRepository, bidRepository, cardService, cursorCodec, clock,
                walletService);
    }

    @Test
    void DB_목록은_size보다_하나_더_조회하고_마지막_항목으로_cursor를_만든다() {
        Auction first = auction(3, AuctionStatus.OPEN, 50_000L, 7);
        Auction second = auction(2, AuctionStatus.OPEN, 45_000L, 5);
        Auction extra = auction(1, AuctionStatus.OPEN, 40_000L, 3);
        given(auctionRepository.searchByCursor(
                eq(""), eq(null), eq(List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)),
                eq(AuctionSort.BID_COUNT.name()), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null),
                eq(true), any(Instant.class), eq(PageRequest.of(0, 3))))
                .willReturn(List.of(first, second, extra));
        given(cardService.getCardSnapshots(List.of(1))).willReturn(Map.of(1, card()));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(3, 2))).willReturn(List.of());

        var response = service.search(
                null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 2));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(3, 2);
        assertThat(response.hasNext()).isTrue();
        assertThat(cursorCodec.decode(response.nextCursor(), AuctionSort.BID_COUNT))
                .isEqualTo(new AuctionCursor(AuctionSort.BID_COUNT, 5L, null, 2));
    }

    @Test
    void DB_상세는_판매자_사진과_카드_원본_이미지를_분리한다() {
        Auction auction = auction(1, AuctionStatus.OPEN, 42_000L, 0);
        AuctionImage image = new AuctionImage(auction, "/uploads/front.png");
        ReflectionTestUtils.setField(image, "id", 11);
        given(auctionRepository.findById(1)).willReturn(Optional.of(auction));
        given(cardService.getCardSnapshot(1)).willReturn(card());
        given(auctionImageRepository.findByAuctionIdOrderById(1)).willReturn(List.of(image));

        var response = service.getDetail(null, 1);

        assertThat(response.card().thumbnailUrl()).isEqualTo("/cards/original.png");
        assertThat(response.photos()).extracting(photo -> photo.url()).containsExactly("/uploads/front.png");
        verifyNoInteractions(bidRepository);
    }

    @Test
    void DB_입찰_내역은_WON_입찰을_최고_입찰로_표시한다() {
        Auction auction = auction(1, AuctionStatus.ENDED, 45_000L, 1);
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.WON);
        given(auctionRepository.findById(1)).willReturn(Optional.of(auction));
        given(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(1, PageRequest.of(0, 20)))
                .willReturn(new PageImpl<>(List.of(winningBid)));
        given(bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                1, List.of(BidStatus.LEADING, BidStatus.WON))).willReturn(Optional.of(winningBid));

        var response = service.getBids(1, new PageRequestDto(0, 20));

        assertThat(response.content()).singleElement().extracting(item -> item.isHighest()).isEqualTo(true);
    }

    @Test
    void DB_입찰_컨텍스트는_낙찰자의_WON을_LEADING으로_변환하고_전달받은_지갑을_사용한다() {
        Auction auction = auction(1, AuctionStatus.ENDED, 45_000L, 1);
        Bid winningBid = bid(1L, 3, auction, 45_000L, BidStatus.WON);
        WalletBalanceResponse wallet = new WalletBalanceResponse(145_000L, 45_000L, 100_000L);
        given(auctionRepository.findById(1)).willReturn(Optional.of(auction));
        given(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(1, 3))
                .willReturn(Optional.of(winningBid));
        given(bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(1, PageRequest.of(0, 5)))
                .willReturn(new PageImpl<>(List.of(winningBid)));
        given(bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                1, List.of(BidStatus.LEADING, BidStatus.WON))).willReturn(Optional.of(winningBid));

        var response = service.getBidContext(3, 1, wallet);

        assertThat(response.myBidStatus()).isEqualTo(MyBidStatus.LEADING);
        assertThat(response.wallet().availableBalance()).isEqualTo(100_000L);
        assertThat(response.wallet().frozenBalance()).isEqualTo(45_000L);
    }

    @Test
    void DB_유찰_목록은_판매자와_FAILED_조건의_결과를_반환한다() {
        Auction failed = auction(1, AuctionStatus.FAILED, 42_000L, 0);
        given(auctionRepository.findBySellerIdAndStatusOrderByCloseTimeDesc(2, AuctionStatus.FAILED))
                .willReturn(List.of(failed));
        given(cardService.getCardSnapshots(List.of(1))).willReturn(Map.of(1, card()));

        var response = service.getFailedAuctions(2);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(1);
            assertThat(item.cardName()).isEqualTo("피카츄");
            assertThat(item.startPrice()).isEqualTo(42_000L);
        });
    }

    @Test
    void DB_대시보드는_경매별_가장_최근_입찰만_반환한다() {
        Auction auction = auction(1, AuctionStatus.OPEN, 45_000L, 2);
        Bid latest = bid(2L, 3, auction, 45_000L, BidStatus.LEADING);
        Bid older = bid(1L, 3, auction, 44_000L, BidStatus.OUTBID);
        given(bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(3)).willReturn(List.of(latest, older));
        given(cardService.getCardSnapshots(List.of(1))).willReturn(Map.of(1, card()));
        given(auctionImageRepository.findByAuctionIdInOrderById(List.of(1))).willReturn(List.of());

        var response = service.getDashboardAuctions(3);

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(1);
            assertThat(item.bidAmount()).isEqualTo(45_000L);
        });
    }

    private Auction auction(Integer id, AuctionStatus status, long currentPrice, int bidCount) {
        Instant closeTime = Instant.parse("2026-08-08T01:00:00Z");
        Auction auction = Auction.builder()
                .sellerId(2).itemId(1).auctionName("경매 A").description("카드 상태 설명")
                .startPrice(42_000L).buyNowPrice(100_000L).deliveryFee(3_000L)
                .openTime(closeTime.minus(Duration.ofHours(2))).estimatedCloseTime(closeTime).closeTime(closeTime)
                .bidPriceUnit(1_000L).hyped(false).build();
        ReflectionTestUtils.setField(auction, "id", id);
        ReflectionTestUtils.setField(auction, "status", status);
        ReflectionTestUtils.setField(auction, "currentPrice", currentPrice);
        ReflectionTestUtils.setField(auction, "bidCount", bidCount);
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, long price, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, price, Instant.parse("2026-08-08T00:00:00Z"), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }

    private CardSnapshot card() {
        return new CardSnapshot(1, "피카츄", "세트", "PSA 10", "JP", "/cards/original.png");
    }
}
