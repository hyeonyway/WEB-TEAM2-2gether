package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;

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
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
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
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class AuctionQueryServiceTest {
    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionImageRepository auctionImageRepository;
    @Mock
    private BidRepository bidRepository;
    @Mock
    private WalletService walletService;
    @Mock
    private CardService cardService;

    private AuctionQueryService auctionQueryService;
    private final AuctionCursorCodec cursorCodec = new AuctionCursorCodec();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        auctionQueryService = new AuctionQueryService(
                auctionRepository,
                auctionImageRepository,
                bidRepository,
                walletService,
                cardService,
                cursorCodec,
                clock
        );
    }

    @Test
    void 경매_목록은_size보다_하나_더_조회하고_마지막_항목으로_다음_cursor를_만든다() {
        Auction first = auction(3, AuctionStatus.OPEN, 50_000L, 7);
        Auction second = auction(2, AuctionStatus.OPEN, 45_000L, 5);
        Auction extra = auction(1, AuctionStatus.OPEN, 40_000L, 3);
        when(auctionRepository.searchByCursor(
                eq(""), eq(null), eq(List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)),
                eq(AuctionSort.BID_COUNT.name()), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(true), any(Instant.class),
                eq(PageRequest.of(0, 3))
        )).thenReturn(List.of(first, second, extra));
        when(cardService.getCardSnapshots(List.of(1))).thenReturn(Map.of(1, card(1)));
        when(auctionImageRepository.findByAuctionIdInOrderById(List.of(3, 2)))
                .thenReturn(List.of());

        var response = auctionQueryService.search(
                null,
                new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 2)
        );

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(3, 2);
        assertThat(response.hasNext()).isTrue();
        assertThat(cursorCodec.decode(response.nextCursor(), AuctionSort.BID_COUNT))
                .isEqualTo(new AuctionCursor(AuctionSort.BID_COUNT, 5L, null, 2));
    }

    @Test
    void Redis_활성_경매_snapshot으로_목록의_변경_필드를_overlay한다() {
        Instant estimatedCloseTime = Instant.parse("2026-08-08T01:00:00Z");
        Auction auction = endingAuction(estimatedCloseTime, estimatedCloseTime.plusSeconds(90));
        when(auctionRepository.searchByCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(auction));
        when(cardService.getCardSnapshots(List.of(1))).thenReturn(Map.of(1, card(1)));
        when(auctionImageRepository.findByAuctionIdInOrderById(List.of(1))).thenReturn(List.of());
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(null);
        when(reader.readSnapshot(1)).thenReturn(new RedisAuctionRealtimeStateReader.Snapshot(
                AuctionStatus.ENDING, 43_000L, 3_000L, 7, Instant.parse("2026-08-08T01:00:00Z"), 100_000L, 2
        ));
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.LATEST, null, null, 20));

        assertThat(response.content().getFirst())
                .extracting(item -> item.currentPrice(), item -> item.bidCount(), item -> item.status(), item -> item.endsAt())
                .containsExactly(43_000L, 7, AuctionStatus.ENDING, Instant.parse("2026-08-08T01:00:00Z"));
    }

    @Test
    void Redis_실시간_상태를_병합한_상세도_ENDING의_예정_마감을_반환한다() {
        Instant estimatedCloseTime = Instant.parse("2026-08-08T01:00:00Z");
        Auction auction = endingAuction(estimatedCloseTime, estimatedCloseTime.plusSeconds(90));
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.readAuctionState(1)).thenReturn(null);
        when(reader.read(1, null)).thenReturn(new RedisAuctionRealtimeStateReader.RealtimeState(
                AuctionStatus.ENDING, 43_000L, 3_000L, 7, estimatedCloseTime.plusSeconds(90), 100_000L,
                MyBidStatus.NONE, null, List.of()
        ));
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(cardService.getCardSnapshot(1)).thenReturn(card(1));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of());
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.getDetail(null, 1);

        assertThat(response.endsAt()).isEqualTo(estimatedCloseTime);
    }

    @Test
    void 변동_정렬_cursor는_목록이_변경되어도_409없이_다음_페이지를_조회한다() {
        AuctionCursor staleCursor = new AuctionCursor(AuctionSort.PRICE_HIGH, 45_000L, null, 2);
        Auction next = auction(1, AuctionStatus.OPEN, 40_000L, 1);
        when(auctionRepository.searchByCursor(
                eq(""), eq(null), eq(List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)),
                eq(AuctionSort.PRICE_HIGH.name()), eq(null), eq(45_000L), eq(null), eq(null), eq(null), eq(2), eq(true),
                any(Instant.class), eq(PageRequest.of(0, 3))
        )).thenReturn(List.of(next));
        when(cardService.getCardSnapshots(List.of(1))).thenReturn(Map.of(1, card(1)));
        when(auctionImageRepository.findByAuctionIdInOrderById(List.of(1))).thenReturn(List.of());

        var request = new AuctionSearchRequest(
                "", null, AuctionSort.PRICE_HIGH, null, cursorCodec.encode(staleCursor), 2
        );

        var response = auctionQueryService.search(null, request);

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(1);
    }

    @Test
    void 상승률순_cursor는_마지막_경매의_상승률_정렬값을_사용한다() {
        Auction first = auction(2, AuctionStatus.OPEN, 15_000L, 1);
        Auction extra = auction(1, AuctionStatus.OPEN, 12_000L, 1);
        ReflectionTestUtils.setField(first, "changeRateBasisPoints", 5_000L);
        when(auctionRepository.searchByCursor(
                eq(""), eq(null), eq(List.of(AuctionStatus.OPEN, AuctionStatus.ENDING)),
                eq(AuctionSort.CHANGE_HIGH.name()), eq(null), eq(null), eq(null), eq(null), eq(null), eq(null), eq(true),
                any(Instant.class), eq(PageRequest.of(0, 2))
        )).thenReturn(List.of(first, extra));
        when(cardService.getCardSnapshots(List.of(1))).thenReturn(Map.of(1, card(1)));
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
        when(walletService.getBalance(3)).thenReturn(new com.dbidding.wallet.dto.WalletBalanceResponse(145_000L, 45_000L, 100_000L));
        when(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(1, 3))
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
    void 종료된_경매의_상세는_실제_마감시각을_반환한다() {
        Auction auction = auction(AuctionStatus.ENDED);
        Instant actualCloseTime = Instant.now().minusSeconds(30);
        ReflectionTestUtils.setField(auction, "closeTime", actualCloseTime);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(cardService.getCardSnapshot(1)).thenReturn(card(1));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of());
        when(bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(1, 3))
                .thenReturn(Optional.empty());

        var response = auctionQueryService.getDetail(3, 1);

        assertThat(response.endsAt()).isEqualTo(actualCloseTime);
    }

    @Test
    void ENDING_경매_목록의_endsAt은_실제_closeTime이_아니라_얼린_estimatedCloseTime이다() {
        Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = endingAuction(estimatedCloseTime, estimatedCloseTime.plusSeconds(90));
        when(auctionRepository.searchByCursor(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), any(), any()))
                .thenReturn(List.of(auction));
        when(cardService.getCardSnapshots(List.of(1))).thenReturn(Map.of(1, card(1)));
        when(auctionImageRepository.findByAuctionIdInOrderById(List.of(1))).thenReturn(List.of());

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.LATEST, null, null, 20));

        assertThat(response.content().getFirst().endsAt()).isEqualTo(estimatedCloseTime);
        assertThat(response.content().getFirst().endsAt()).isNotEqualTo(auction.getCloseTime());
    }

    @Test
    void ENDING_경매_상세의_endsAt도_얼린_estimatedCloseTime이다() {
        Instant estimatedCloseTime = Instant.parse("2026-08-12T10:00:00Z");
        Auction auction = endingAuction(estimatedCloseTime, estimatedCloseTime.plusSeconds(90));
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(cardService.getCardSnapshot(1)).thenReturn(card(1));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of());

        var response = auctionQueryService.getDetail(null, 1);

        assertThat(response.endsAt()).isEqualTo(estimatedCloseTime);
        assertThat(response.endsAt()).isNotEqualTo(auction.getCloseTime());
    }

    @Test
    void 비로그인_상세_조회는_내_입찰을_조회하지_않는다() {
        Auction auction = auction(AuctionStatus.OPEN);
        when(auctionRepository.findById(1)).thenReturn(Optional.of(auction));
        when(cardService.getCardSnapshot(1)).thenReturn(card(1));
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
        when(cardService.getCardSnapshot(1)).thenReturn(new CardSnapshot(
                1, "Mock Card", "Mock Set", "10", "JP", "/cards/original.png"
        ));
        when(auctionImageRepository.findByAuctionIdOrderById(1)).thenReturn(List.of(first, second));

        var response = auctionQueryService.getDetail(null, 1);

        assertThat(response.card().thumbnailUrl()).isEqualTo("/cards/original.png");
        assertThat(response.photos()).extracting(photo -> photo.url())
                .containsExactly("/uploads/front.png", "/uploads/back.png");
    }

    @Test
    void 판매자의_유찰_경매를_마감_최신순으로_조회한다() {
        Auction failed = auction(1, AuctionStatus.FAILED, 42_000L, 0);
        when(auctionRepository.findBySellerIdAndStatusOrderByCloseTimeDesc(2, AuctionStatus.FAILED))
                .thenReturn(List.of(failed));
        when(cardService.getCardSnapshots(List.of(1))).thenReturn(Map.of(1, card(1)));

        var response = auctionQueryService.getFailedAuctions(2);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().id()).isEqualTo(1);
        assertThat(response.getFirst().cardName()).isEqualTo("Mock Card");
        assertThat(response.getFirst().startPrice()).isEqualTo(42_000L);
        assertThat(response.getFirst().closedAt()).isEqualTo(failed.getCloseTime());
    }

    @Test
    void Redis_활성_경매_목록은_BID_COUNT_기준_내림차순으로_정렬한다() {
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(List.of());
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(2, 10), tuple(3, 1)));
        when(reader.readAuctionState(1)).thenReturn(redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        when(reader.readAuctionState(2)).thenReturn(redisState(2, 10, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        when(reader.readAuctionState(3)).thenReturn(redisState(3, 1, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(2, 1, 3);
    }

    @Test
    void Redis_활성_경매_목록_커서_이후_페이지는_이전_항목을_반복하지_않는다() {
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(List.of());
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(2, 10), tuple(1, 5), tuple(3, 1)));
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(5.0), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(3, 1)));
        when(reader.readAuctionState(1)).thenReturn(redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        when(reader.readAuctionState(2)).thenReturn(redisState(2, 10, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        when(reader.readAuctionState(3)).thenReturn(redisState(3, 1, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var firstPage = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 2));
        assertThat(firstPage.content()).extracting(item -> item.id()).containsExactly(2, 1);
        assertThat(firstPage.hasNext()).isTrue();

        var secondPage = auctionQueryService.search(null,
                new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, firstPage.nextCursor(), 2));

        assertThat(secondPage.content()).extracting(item -> item.id()).containsExactly(3);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void PRICE_LOW_정렬은_동점일_때_auctionId_내림차순으로_tie_break한다() {
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(List.of());
        when(reader.activeIdsBatch(eq("auction:active:by-price"), eq(false), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(3, 40_000), tuple(5, 40_000)));
        when(reader.readAuctionState(3)).thenReturn(redisState(3, 0, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        when(reader.readAuctionState(5)).thenReturn(redisState(5, 0, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.PRICE_LOW, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(5, 3);
    }

    @Test
    void Redis_활성_경매_목록은_psaGrade_필터를_적용한다() {
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(List.of());
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(2, 3)));
        when(reader.readAuctionState(1)).thenReturn(redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        when(reader.readAuctionState(2)).thenReturn(redisState(2, 3, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "9"));
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", "10", AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(1);
    }

    @Test
    void 한_배치가_필터로_다_걸러지면_다음_배치를_추가로_가져온다() {
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(List.of());
        List<ZSetOperations.TypedTuple<String>> firstBatch = new java.util.ArrayList<>();
        for (int id = 100; id < 150; id++) firstBatch.add(tuple(id, 200 - id));
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(firstBatch);
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(51.0), eq(1L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5)));
        for (int id = 100; id < 150; id++) when(reader.readAuctionState(id)).thenReturn(null);
        when(reader.readAuctionState(1)).thenReturn(redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(1);
    }

    @Test
    void 배치_크기를_넘는_동점이_있어도_중복_없이_전부_가져온다() {
        RedisAuctionRealtimeStateReader reader = mock(RedisAuctionRealtimeStateReader.class);
        when(reader.activeAuctionIds()).thenReturn(List.of());
        // Redis는 동점 구간에서 멤버 문자열 lex 순서로 반환하므로, 숫자 auctionId 순서와 다른
        // 임의의 순서로 50개(1번째 배치)와 나머지 10개(2번째 배치)를 나눠 돌려주도록 시뮬레이션한다.
        List<ZSetOperations.TypedTuple<String>> firstBatch = new java.util.ArrayList<>();
        for (int id = 60; id > 10; id--) firstBatch.add(tuple(id, 5));
        List<ZSetOperations.TypedTuple<String>> secondBatch = new java.util.ArrayList<>();
        for (int id = 10; id >= 1; id--) secondBatch.add(tuple(id, 5));
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(firstBatch);
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(5.0), eq(50L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(secondBatch);
        for (int id = 1; id <= 60; id++) {
            when(reader.readAuctionState(id)).thenReturn(redisState(id, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"));
        }
        ReflectionTestUtils.setField(auctionQueryService, "realtimeStateReader", reader);

        var response = auctionQueryService.search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 60));

        assertThat(response.content()).hasSize(60);
        assertThat(response.content()).extracting(item -> item.id()).doesNotHaveDuplicates();
        List<Integer> expectedDescending = new java.util.ArrayList<>();
        for (int id = 60; id >= 1; id--) expectedDescending.add(id);
        assertThat(response.content()).extracting(item -> item.id()).containsExactlyElementsOf(expectedDescending);
        assertThat(response.hasNext()).isFalse();
    }

    private ZSetOperations.TypedTuple<String> tuple(Integer auctionId, double score) {
        return new DefaultTypedTuple<>(String.valueOf(auctionId), score);
    }

    private RedisAuctionRealtimeStateReader.AuctionState redisState(
            Integer auctionId, int bidCount, long currentPrice, long startPrice, Instant openTime, String psaGrade
    ) {
        return new RedisAuctionRealtimeStateReader.AuctionState(
                auctionId, AuctionStatus.OPEN, 2, 1, "카드 " + auctionId, "세트", psaGrade, "JP", "/thumb.png",
                "경매 " + auctionId, "설명", null, null, null, false,
                startPrice, currentPrice, 1_000L, bidCount, null, 3_000L,
                openTime, openTime.plus(Duration.ofHours(1)), List.of()
        );
    }

    private CardSnapshot card(Integer itemId) {
        return new CardSnapshot(itemId, "Mock Card", "Mock Set", "10", "JP", "/mock/card.png");
    }

    private Auction auction(AuctionStatus status) {
        return auction(1, status, 45_000L, 0);
    }

    private Auction auction(Integer id, AuctionStatus status, Long currentPrice, Integer bidCount) {
        Instant closeTime = Instant.now().minus(Duration.ofMinutes(1));
        Auction auction = Auction.builder()
                .sellerId(2)
                .itemId(1)
                .auctionName("경매 A")
                .description("카드 상태 설명")
                .startPrice(42_000L)
                .buyNowPrice(100_000L)
                .deliveryFee(3_000L)
                .openTime(closeTime.minus(Duration.ofHours(2)))
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

    private Auction endingAuction(Instant estimatedCloseTime, Instant realCloseTime) {
        Auction auction = Auction.builder()
                .sellerId(2).itemId(1).auctionName("경매 A").description("카드 상태 설명")
                .startPrice(42_000L).buyNowPrice(100_000L).deliveryFee(3_000L)
                .openTime(estimatedCloseTime.minus(Duration.ofHours(2)))
                .estimatedCloseTime(estimatedCloseTime).closeTime(estimatedCloseTime)
                .bidPriceUnit(1_000L).hyped(false)
                .build();
        ReflectionTestUtils.setField(auction, "id", 1);
        auction.enterEnding(Duration.between(estimatedCloseTime, realCloseTime));
        return auction;
    }

    private Bid bid(Long id, Integer bidderId, Auction auction, Long bidPrice, BidStatus status) {
        Bid bid = new Bid(bidderId, auction, bidPrice, Instant.now().minus(Duration.ofMinutes(5)), status);
        ReflectionTestUtils.setField(bid, "id", id);
        return bid;
    }
}
