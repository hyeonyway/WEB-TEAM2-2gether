package com.dbidding.auction.service.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.service.WalletService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.ZSetOperations;

/**
 * {@code AuctionQueryService}(파사드)에서 추출한 Redis 실시간 조회 로직 전용 테스트.
 * DB 폴백 라우팅 자체는 {@code AuctionQueryServiceTest}가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class RedisAuctionQueryServiceTest {
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
    @Mock
    private RedisAuctionRealtimeStateReader reader;

    private final AuctionCursorCodec cursorCodec = new AuctionCursorCodec();

    private RedisAuctionQueryService service() {
        return new RedisAuctionQueryService(walletService, cursorCodec, reader, null);
    }

    @Test
    void Redis_입찰_내역은_한번_읽은_stored_state를_최근_입찰_조회에_재사용한다() {
        var state = redisState(1, 3, 43_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        var stored = new RedisAuctionRealtimeStateReader.StoredAuctionState(state, 7);
        var recentBid = new BidResponses.BidSummary(
                11L, 43_000L, "user-7***", true, Instant.parse("2026-08-01T00:10:00Z"));
        when(reader.readStoredAuctionState(1)).thenReturn(stored);
        when(reader.read(stored, null)).thenReturn(new RedisAuctionRealtimeStateReader.RealtimeState(
                AuctionStatus.OPEN, 43_000L, 1_000L, 3, state.closeTime(), null,
                MyBidStatus.NONE, null, List.of(recentBid)));

        var response = service().getBids(1, new PageRequestDto(0, 20));

        assertThat(response.content()).containsExactly(recentBid);
    }

    @Test
    void Redis_입찰_컨텍스트는_hit한_stored_state를_재사용한다() {
        var state = redisState(1, 3, 43_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        var stored = new RedisAuctionRealtimeStateReader.StoredAuctionState(state, 7);
        when(reader.readStoredAuctionState(1)).thenReturn(stored);
        when(reader.read(stored, 7)).thenReturn(new RedisAuctionRealtimeStateReader.RealtimeState(
                AuctionStatus.OPEN, 43_000L, 1_000L, 3, state.closeTime(), null,
                MyBidStatus.LEADING, 43_000L, List.of()));
        when(walletService.getBalance(7)).thenReturn(
                new com.dbidding.wallet.dto.WalletBalanceResponse(100_000L, 43_000L, 57_000L));

        var response = service().getBidContext(7, 1);

        assertThat(response.currentPrice()).isEqualTo(43_000L);
        assertThat(response.myBidStatus()).isEqualTo(MyBidStatus.LEADING);
        assertThat(response.myBidAmount()).isEqualTo(43_000L);
        assertThat(response.wallet().availableBalance()).isEqualTo(57_000L);
    }

    @Test
    void Redis_활성_경매_목록은_batch_state와_참여_입찰_상태로_응답을_조립한다() {
        var first = redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        var leading = redisState(2, 10, 43_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        var last = redisState(3, 1, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(2, 10), tuple(3, 1)));
        when(reader.readAuctionStates(List.of(1, 2, 3)))
                .thenReturn(Map.of(1, first, 2, leading, 3, last));
        when(reader.readMyBidStates(List.of(2, 1, 3), 7))
                .thenReturn(Map.of(2, new RedisAuctionRealtimeStateReader.MyBidState(MyBidStatus.LEADING, 43_000L)));

        var response = service().search(
                7, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(2, 1, 3);
        assertThat(response.content().getFirst().myBidStatus()).isEqualTo(MyBidStatus.LEADING);
        assertThat(response.content().getFirst().myBidAmount()).isEqualTo(43_000L);
        assertThat(response.content().get(1).myBidStatus()).isEqualTo(MyBidStatus.NONE);
        assertThat(response.content().get(1).myBidAmount()).isNull();
    }

    @Test
    void Redis_목록_command_오류는_cache_miss처럼_DB_fallback하지_않는다() {
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5)));
        when(reader.readAuctionStates(List.of(1)))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> service().search(
                7, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20)))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("redis unavailable");

        verifyNoInteractions(auctionRepository, auctionImageRepository, bidRepository, cardService);
    }

    @Test
    void Redis_상세_command_오류는_cache_miss처럼_DB_fallback하지_않는다() {
        when(reader.readStoredAuctionState(1)).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> service().getDetail(null, 1))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("redis unavailable");

        verifyNoInteractions(auctionRepository, auctionImageRepository, bidRepository, cardService);
    }

    @Test
    void Redis_입찰내역_command_오류는_cache_miss처럼_DB_fallback하지_않는다() {
        when(reader.readStoredAuctionState(1)).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> service().getBids(1, new PageRequestDto(0, 20)))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("redis unavailable");

        verifyNoInteractions(auctionRepository, auctionImageRepository, bidRepository, cardService);
    }

    @Test
    void Redis_입찰_컨텍스트_command_오류는_cache_miss처럼_DB_fallback하지_않는다() {
        when(reader.readStoredAuctionState(1)).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThatThrownBy(() -> service().getBidContext(7, 1))
                .isInstanceOf(RedisConnectionFailureException.class)
                .hasMessage("redis unavailable");

        verifyNoInteractions(auctionRepository, auctionImageRepository, bidRepository, cardService, walletService);
    }

    @Test
    void Redis_활성_경매_목록은_BID_COUNT_기준_내림차순으로_정렬한다() {
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(2, 10), tuple(3, 1)));
        when(reader.readAuctionStates(List.of(1, 2, 3))).thenReturn(Map.of(
                1, redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"),
                2, redisState(2, 10, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"),
                3, redisState(3, 1, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10")
        ));

        var response = service().search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(2, 1, 3);
    }

    @Test
    void Redis_활성_경매_목록_커서_이후_페이지는_이전_항목을_반복하지_않는다() {
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(2, 10), tuple(1, 5), tuple(3, 1)));
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(5.0), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(3, 1)));
        var first = redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        var second = redisState(2, 10, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        var third = redisState(3, 1, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10");
        when(reader.readAuctionStates(List.of(2, 1, 3))).thenReturn(Map.of(1, first, 2, second, 3, third));
        when(reader.readAuctionStates(List.of(1, 3))).thenReturn(Map.of(1, first, 3, third));

        RedisAuctionQueryService service = service();
        var firstPage = service.search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 2));
        assertThat(firstPage.content()).extracting(item -> item.id()).containsExactly(2, 1);
        assertThat(firstPage.hasNext()).isTrue();

        var secondPage = service.search(null,
                new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, firstPage.nextCursor(), 2));

        assertThat(secondPage.content()).extracting(item -> item.id()).containsExactly(3);
        assertThat(secondPage.hasNext()).isFalse();
    }

    @Test
    void PRICE_LOW_정렬은_동점일_때_auctionId_내림차순으로_tie_break한다() {
        when(reader.activeIdsBatch(eq("auction:active:by-price"), eq(false), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(3, 40_000), tuple(5, 40_000)));
        when(reader.readAuctionStates(List.of(3, 5))).thenReturn(Map.of(
                3, redisState(3, 0, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10"),
                5, redisState(5, 0, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10")
        ));

        var response = service().search(null, new AuctionSearchRequest("", null, AuctionSort.PRICE_LOW, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(5, 3);
    }

    @Test
    void Redis_활성_경매_목록은_psaGrade_필터를_적용한다() {
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5), tuple(2, 3)));
        when(reader.readAuctionStates(List.of(1, 2))).thenReturn(Map.of(
                1, redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "PSA 10"),
                2, redisState(2, 3, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "9")
        ));

        var response = service().search(null, new AuctionSearchRequest("", "10", AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(1);
    }

    @Test
    void 마감_임박순은_Redis_closeTime_순서와_동일하게_정렬한다() {
        when(reader.activeIdsBatch(eq("auction:active:by-close-time"), eq(false), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 10), tuple(2, 20), tuple(3, 30), tuple(4, 40)));
        var endingFirst = redisState(1, AuctionStatus.ENDING, Instant.parse("2026-08-01T03:00:00Z"));
        var earlierOpen = redisState(2, AuctionStatus.OPEN, Instant.parse("2026-08-01T01:00:00Z"));
        var laterOpen = redisState(3, AuctionStatus.OPEN, Instant.parse("2026-08-01T02:00:00Z"));
        var endingSecond = redisState(4, AuctionStatus.ENDING, Instant.parse("2026-08-01T04:00:00Z"));
        when(reader.readAuctionStates(List.of(1, 2, 3, 4))).thenReturn(Map.of(
                1, endingFirst, 2, earlierOpen, 3, laterOpen, 4, endingSecond));

        var response = service().search(null, new AuctionSearchRequest("", null, AuctionSort.ENDING_SOON, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(2, 3, 1, 4);
    }

    @Test
    void 한_배치가_필터로_다_걸러지면_다음_배치를_추가로_가져온다() {
        List<ZSetOperations.TypedTuple<String>> firstBatch = new java.util.ArrayList<>();
        for (int id = 100; id < 150; id++) firstBatch.add(tuple(id, 200 - id));
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(null), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(firstBatch);
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(51.0), eq(1L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of(tuple(1, 5)));
        List<Integer> firstBatchIds = firstBatch.stream().map(tuple -> Integer.valueOf(tuple.getValue())).toList();
        when(reader.readAuctionStates(firstBatchIds)).thenReturn(Map.of());
        when(reader.readAuctionStates(List.of(1))).thenReturn(Map.of(
                1, redisState(1, 5, 40_000L, 40_000L, Instant.parse("2026-08-01T00:00:00Z"), "10")));

        var response = service().search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 20));

        assertThat(response.content()).extracting(item -> item.id()).containsExactly(1);
    }

    @Test
    void 배치_크기를_넘는_동점이_있어도_중복_없이_전부_가져온다() {
        // 배치 크기는 고정(#552)이지만 그 값과 무관하게 "한 배치로 동점 전부를 못 채우면
        // 다음 배치를 이어서 가져온다"가 성립해야 하므로, 요청한 batchSize를 실제로 반영해
        // 잘라주는 가짜 Redis를 흉내낸다.
        List<Integer> allIdsDescending = new java.util.ArrayList<>();
        for (int id = 60; id >= 1; id--) allIdsDescending.add(id);
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyInt()))
                .thenAnswer(invocation -> {
                    long withinBoundOffset = invocation.getArgument(3);
                    int batchSize = invocation.getArgument(4);
                    List<ZSetOperations.TypedTuple<String>> slice = new java.util.ArrayList<>();
                    for (int i = (int) withinBoundOffset; i < Math.min(withinBoundOffset + batchSize, allIdsDescending.size()); i++) {
                        slice.add(tuple(allIdsDescending.get(i), 5));
                    }
                    return slice;
                });
        when(reader.readAuctionStates(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            List<Integer> ids = invocation.getArgument(0);
            Map<Integer, RedisAuctionRealtimeStateReader.AuctionState> states = new java.util.LinkedHashMap<>();
            ids.forEach(id -> states.put(id, redisState(id, 5, 40_000L, 40_000L,
                    Instant.parse("2026-08-01T00:00:00Z"), "10")));
            return states;
        });

        var response = service().search(null, new AuctionSearchRequest("", null, AuctionSort.BID_COUNT, null, null, 60));

        assertThat(response.content()).hasSize(60);
        assertThat(response.content()).extracting(item -> item.id()).doesNotHaveDuplicates();
        assertThat(response.content()).extracting(item -> item.id()).containsExactlyElementsOf(allIdsDescending);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    void 동점_score가_여러_배치에_걸쳐도_cursor_이전_항목을_다시_반환하지_않는다() {
        List<Integer> firstBatch = java.util.stream.IntStream.rangeClosed(101, 150).boxed().toList();
        List<Integer> secondBatch = java.util.stream.IntStream.rangeClosed(51, 100).boxed().toList();
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(5.0), eq(0L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(firstBatch.stream().map(id -> (ZSetOperations.TypedTuple<String>) new DefaultTypedTuple<>(String.valueOf(id), 5.0)).toList());
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(5.0), eq(50L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(secondBatch.stream().map(id -> (ZSetOperations.TypedTuple<String>) new DefaultTypedTuple<>(String.valueOf(id), 5.0)).toList());
        when(reader.activeIdsBatch(eq("auction:active:by-bid-count"), eq(true), eq(5.0), eq(100L), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(List.of());
        when(reader.readAuctionStates(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
            List<Integer> ids = invocation.getArgument(0);
            Map<Integer, RedisAuctionRealtimeStateReader.AuctionState> states = new java.util.LinkedHashMap<>();
            ids.forEach(id -> states.put(id, redisState(id, 5, 40_000L, 40_000L,
                    Instant.parse("2026-08-01T00:00:00Z"), "10")));
            return states;
        });

        var cursor = new AuctionCursor(AuctionSort.BID_COUNT, 5L, null, 100);
        var response = service().search(null,
                new AuctionSearchRequest("", "PSA 10", AuctionSort.BID_COUNT, null, cursorCodec.encode(cursor), 60));

        assertThat(response.content()).extracting(item -> item.id()).containsExactlyElementsOf(
                java.util.stream.IntStream.iterate(99, id -> id >= 51, id -> id - 1).boxed().toList());
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

    private RedisAuctionRealtimeStateReader.AuctionState redisState(
            Integer auctionId, AuctionStatus status, Instant closeTime
    ) {
        Instant openTime = closeTime.minus(Duration.ofHours(1));
        return new RedisAuctionRealtimeStateReader.AuctionState(
                auctionId, status, 2, 1, "카드 " + auctionId, "세트", "10", "JP", "/thumb.png",
                "경매 " + auctionId, "설명", null, null, null, false,
                40_000L, 40_000L, 1_000L, 0, null, 3_000L,
                openTime, closeTime, List.of()
        );
    }
}
