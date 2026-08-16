package com.dbidding.auction.service;

import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.auction.bid.RedisAuctionStateSeeder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
@RequiredArgsConstructor
public class AuctionQueryService {
    private final WalletService walletService;
    private final DbAuctionQueryService dbAuctionQueryService;
    private final AuctionCursorCodec auctionCursorCodec;
    @Autowired(required = false)
    private RedisAuctionRealtimeStateReader realtimeStateReader;
    @Autowired(required = false)
    private RedisAuctionStateSeeder stateSeeder;

    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            Integer userId,
            AuctionSearchRequest request
    ) {
        return realtimeStateReader == null
                ? dbAuctionQueryService.search(userId, request)
                : searchRedisActiveAuctions(userId, request);
    }

    private static final int SORT_ZSET_FETCH_BATCH_SIZE = 50;
    private static final int SORT_ZSET_MAX_BATCHES = 20;

    private AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> searchRedisActiveAuctions(
            Integer userId,
            AuctionSearchRequest request
    ) {
        AuctionSort sort = request.sortOrDefault();
        int size = request.sizeOrDefault();
        AuctionCursor cursor = request.cursor() == null || request.cursor().isBlank() ? null : auctionCursorCodec.decode(request.cursor(), sort);
        List<RedisAuctionRealtimeStateReader.AuctionState> page = fetchRedisSortedPage(request, sort, cursor, size + 1);
        boolean hasNext = page.size() > size;
        List<RedisAuctionRealtimeStateReader.AuctionState> content = hasNext ? page.subList(0, size) : page;
        List<AuctionResponses.AuctionSummary> items = content.stream().map(state -> redisSummary(state, userId)).toList();
        String nextCursor = hasNext ? auctionCursorCodec.encode(redisCursorOf(content.getLast(), sort)) : null;
        return new AuctionResponses.CursorPage<>(items, nextCursor, hasNext);
    }

    /**
     * 정렬 기준별 ZSET에서 커서 이후 필요한 만큼만 배치로 가져온다. 하나의 배치가 keyword/psaGrade/status
     * 필터로 거의 다 걸러지는 경우를 대비해, 부족하면 이전 배치의 마지막 원시 항목 score부터 이어서 최대
     * SORT_ZSET_MAX_BATCHES번까지 추가로 가져온다(전체 스캔 방지용 상한 — 필터가 매우 좁으면 이 상한 안에서
     * 요청한 size보다 적게 반환될 수 있다).
     */
    private List<RedisAuctionRealtimeStateReader.AuctionState> fetchRedisSortedPage(
            AuctionSearchRequest request, AuctionSort sort, AuctionCursor cursor, int limit
    ) {
        String zsetKey = sortZSetKey(sort);
        boolean descending = sort != AuctionSort.PRICE_LOW && sort != AuctionSort.ENDING_SOON;
        Double initialBound = cursor == null ? null : cursorScore(cursor, sort);
        Double bound = initialBound;
        long withinBoundOffset = 0;
        List<RedisAuctionRealtimeStateReader.AuctionState> collected = new ArrayList<>();
        boolean exhausted = false;
        // #529: keyword/psaGrade 필터가 없으면 ZSET에서 나온 순서 그대로가 결과라 걸러질 항목이
        // 없다 — limit만큼만 가져오면 된다. 필터가 있으면 몇 개가 걸러질지 미리 알 수 없으므로
        // (Redis가 텍스트/등급 필터를 직접 못 함, #530) 지금처럼 여유분(고정 배치)을 유지한다.
        boolean hasNoFilter = request.keywordOrDefault().isBlank() && (request.psaGrade() == null || request.psaGrade().isBlank());
        int fetchBatchSize = hasNoFilter ? limit : SORT_ZSET_FETCH_BATCH_SIZE;
        for (int batch = 0; collected.size() < limit && !exhausted && batch < SORT_ZSET_MAX_BATCHES; batch++) {
            List<ZSetOperations.TypedTuple<String>> raw = realtimeStateReader.activeIdsBatch(zsetKey, descending, bound, withinBoundOffset, fetchBatchSize);
            if (raw.isEmpty()) {
                exhausted = true;
                break;
            }
            // 커서 경계 필터는 "아직 사용자 커서와 같은 score(동점 구간) 안에 있을 때"만 적용한다.
            // score가 바뀌면 그 뒤로는 전부 커서 이후이므로(순서상 모호함이 없음) 더 적용할 필요가 없다.
            AuctionCursor cursorForFilter = cursor != null && java.util.Objects.equals(bound, initialBound) ? cursor : null;
            List<RedisAuctionRealtimeStateReader.AuctionState> filtered = raw.stream()
                    .map(tuple -> realtimeStateReader.readAuctionState(Integer.valueOf(tuple.getValue())))
                    .filter(Objects::nonNull)
                    .filter(state -> request.status() == null || state.status() == request.status())
                    .filter(state -> request.keywordOrDefault().isBlank()
                            || state.auctionName().toLowerCase().contains(request.keywordOrDefault().toLowerCase())
                            || state.cardName().toLowerCase().contains(request.keywordOrDefault().toLowerCase()))
                    .filter(state -> request.psaGrade() == null || request.psaGrade().isBlank()
                            || normalizedPsaGrade(request.psaGrade()).equals(normalizedPsaGrade(state.cardPsaGrade())))
                    .sorted(redisComparator(sort))
                    .filter(state -> cursorForFilter == null || isAfterCursor(state, cursorForFilter, sort))
                    .toList();
            collected.addAll(filtered);
            double lastScore = raw.getLast().getScore();
            long itemsAtLastScore = raw.stream().filter(tuple -> tuple.getScore() == lastScore).count();
            // 이 배치 전체가 여전히 같은 bound(score)에 머물러 있으면 - batchSize를 넘는 동점이 있다는 뜻이므로,
            // 같은 score 안에서 이미 가져온 만큼 offset을 늘려 다음 호출이 이어서 가져오게 한다. score가
            // 바뀌었으면 그 새 score에서 이 배치가 이미 소비한 만큼만 offset으로 남긴다.
            if (bound != null && lastScore == bound) {
                withinBoundOffset += raw.size();
            } else {
                bound = lastScore;
                withinBoundOffset = itemsAtLastScore;
            }
            if (raw.size() < fetchBatchSize) exhausted = true;
        }
        return collected.size() > limit ? collected.subList(0, limit) : collected;
    }

    private String normalizedPsaGrade(String psaGrade) {
        return psaGrade == null ? "" : psaGrade.trim().replaceFirst("(?i)^PSA\\s+", "").trim();
    }

    private String sortZSetKey(AuctionSort sort) {
        return switch (sort) {
            case LATEST -> "auction:active:by-open-time";
            case BID_COUNT -> "auction:active:by-bid-count";
            case PRICE_HIGH, PRICE_LOW -> "auction:active:by-price";
            case CHANGE_HIGH -> "auction:active:by-change-rate";
            case ENDING_SOON -> "auction:active:by-close-time";
        };
    }

    private Double cursorScore(AuctionCursor cursor, AuctionSort sort) {
        return (sort == AuctionSort.LATEST || sort == AuctionSort.ENDING_SOON)
                ? (double) cursor.timeValue().toEpochMilli() : (double) cursor.value();
    }

    /** score에 auctionId를 인코딩하지 않으므로, 커서 경계(동점 tie-break 포함)는 여기서 직접 비교한다. */
    private boolean isAfterCursor(RedisAuctionRealtimeStateReader.AuctionState state, AuctionCursor cursor, AuctionSort sort) {
        if (sort == AuctionSort.LATEST || sort == AuctionSort.ENDING_SOON) {
            int compared = (sort == AuctionSort.LATEST ? state.openTime() : state.closeTime()).compareTo(cursor.timeValue());
            if (compared != 0) return sort == AuctionSort.ENDING_SOON ? compared > 0 : compared < 0;
            return state.auctionId() < cursor.auctionId();
        }
        long value = switch (sort) {
            case BID_COUNT -> state.bidCount();
            case PRICE_HIGH, PRICE_LOW -> state.currentPrice();
            case CHANGE_HIGH -> changeRateBasisPoints(state);
            case ENDING_SOON -> throw new IllegalStateException("unreachable");
            case LATEST -> throw new IllegalStateException("unreachable");
        };
        int compared = Long.compare(value, cursor.value());
        if (sort == AuctionSort.PRICE_LOW) {
            if (compared != 0) return compared > 0;
            return state.auctionId() < cursor.auctionId();
        }
        if (compared != 0) return compared < 0;
        return state.auctionId() < cursor.auctionId();
    }

    private java.util.Comparator<RedisAuctionRealtimeStateReader.AuctionState> redisComparator(AuctionSort sort) {
        return switch (sort) {
            case LATEST -> java.util.Comparator.comparing(RedisAuctionRealtimeStateReader.AuctionState::openTime).reversed()
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId, java.util.Comparator.reverseOrder());
            case BID_COUNT -> java.util.Comparator.comparingInt(RedisAuctionRealtimeStateReader.AuctionState::bidCount).reversed()
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId, java.util.Comparator.reverseOrder());
            case PRICE_HIGH -> java.util.Comparator.comparingLong(RedisAuctionRealtimeStateReader.AuctionState::currentPrice).reversed()
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId, java.util.Comparator.reverseOrder());
            case PRICE_LOW -> java.util.Comparator.comparingLong(RedisAuctionRealtimeStateReader.AuctionState::currentPrice)
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId, java.util.Comparator.reverseOrder());
            case CHANGE_HIGH -> java.util.Comparator.comparingLong(
                            (RedisAuctionRealtimeStateReader.AuctionState state) -> changeRateBasisPoints(state)).reversed()
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId, java.util.Comparator.reverseOrder());
            case ENDING_SOON -> java.util.Comparator.comparing(
                            (RedisAuctionRealtimeStateReader.AuctionState state) -> state.status() != AuctionStatus.ENDING
                    )
                    .thenComparing(
                            state -> state.status() == AuctionStatus.OPEN ? state.closeTime() : Instant.EPOCH
                    )
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId, java.util.Comparator.reverseOrder());
        };
    }

    private AuctionCursor redisCursorOf(RedisAuctionRealtimeStateReader.AuctionState state, AuctionSort sort) {
        Long value = switch (sort) {
            case LATEST -> null;
            case BID_COUNT -> (long) state.bidCount();
            case PRICE_HIGH, PRICE_LOW -> state.currentPrice();
            case CHANGE_HIGH -> changeRateBasisPoints(state);
            case ENDING_SOON -> null;
        };
        Instant timeValue = sort == AuctionSort.LATEST ? state.openTime()
                : sort == AuctionSort.ENDING_SOON ? state.closeTime() : null;
        return new AuctionCursor(sort, value, timeValue, state.auctionId());
    }

    private long changeRateBasisPoints(RedisAuctionRealtimeStateReader.AuctionState state) {
        return (state.currentPrice() - state.startPrice()) * 10_000L / state.startPrice();
    }

    private AuctionResponses.AuctionSummary redisSummary(RedisAuctionRealtimeStateReader.AuctionState state, Integer userId) {
        CardSnapshot card = redisCardSnapshot(state);
        // #529: 목록 항목은 이 유저의 입찰 상태/금액만 필요하다 — read()를 재사용하면 항목마다
        // state에 이미 있는 스냅샷을 중복 재조회하고, 응답에 쓰지도 않는 recentBids(XREVRANGE)까지
        // 매번 다시 읽어온다. readMyBidSummary()는 HGETALL 1번으로 끝낸다.
        RedisAuctionRealtimeStateReader.MyBidSummary myBid = realtimeStateReader.readMyBidSummary(state.auctionId(), userId);
        if (myBid == null) myBid = RedisAuctionRealtimeStateReader.MyBidSummary.NONE;
        return AuctionResponses.AuctionSummary.builder()
                .id(state.auctionId()).card(cardSummary(card)).seller(sellerSummary(state.sellerId()))
                .startPrice(state.startPrice()).currentPrice(state.currentPrice()).bidIncrement(state.bidIncrement())
                .minimumBid(state.buyNowPrice() == null ? state.currentPrice() + state.bidIncrement()
                        : Math.min(state.currentPrice() + state.bidIncrement(), state.buyNowPrice()))
                .bidCount(state.bidCount()).buyNowPrice(state.buyNowPrice()).startsAt(state.openTime()).endsAt(publicCloseTime(state))
                .status(state.status()).myBidStatus(myBid.status()).myBidAmount(myBid.amount()).build();
    }

    private CardSnapshot redisCardSnapshot(RedisAuctionRealtimeStateReader.AuctionState state) {
        return new CardSnapshot(
                state.itemId(), state.cardName(), state.cardSetName(), state.cardPsaGrade(), state.cardLanguage(), state.cardThumbnailUrl()
        );
    }

    public List<AuctionResponses.DashboardAuction> getDashboardAuctions(Integer userId) {
        return dbAuctionQueryService.getDashboardAuctions(userId);
    }

    public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(Integer sellerId) {
        return dbAuctionQueryService.getFailedAuctions(sellerId);
    }

    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        seedAuctionIfRequired(auctionId);
        RedisAuctionRealtimeStateReader.AuctionState redisState = realtimeStateReader == null ? null
                : realtimeStateReader.readAuctionState(auctionId);
        if (redisState != null) {
            return redisDetail(redisState, userId);
        }
        return dbAuctionQueryService.getDetail(userId, auctionId);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
        if (realtimeStateReader != null && realtimeStateReader.readAuctionState(auctionId) != null) {
            RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(auctionId, null);
            if (realtime == null) throw AuctionException.notFound();
            List<BidResponses.BidSummary> content = realtime.recentBids();
            return new AuctionResponses.Page<>(content, 0, request.sizeOrDefault(), content.size(), false);
        }
        return dbAuctionQueryService.getBids(auctionId, request);
    }

    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        if (realtimeStateReader == null) {
            return dbAuctionQueryService.getBidContext(userId, auctionId);
        }
        seedAuctionIfRequired(auctionId);
        WalletBalanceResponse wallet = walletService.getBalance(userId);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(auctionId, userId);
        if (realtime != null) {
            return BidResponses.BidContext.builder()
                    .auctionId(auctionId).status(realtime.status()).currentPrice(realtime.currentPrice())
                    .minimumBid(realtime.currentPrice() + realtime.bidIncrement()).bidIncrement(realtime.bidIncrement())
                    .myBidStatus(realtime.myBidStatus()).myBidAmount(realtime.myBidAmount())
                    .wallet(new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance()))
                    .recentBids(realtime.recentBids()).build();
        }
        return dbAuctionQueryService.getBidContext(userId, auctionId, wallet);
    }

    private void seedAuctionIfRequired(Integer auctionId) {
        if (realtimeStateReader != null && realtimeStateReader.readAuctionState(auctionId) == null && stateSeeder != null) {
            stateSeeder.seedIfAbsent(auctionId);
        }
    }

    private AuctionResponses.AuctionDetail redisDetail(
            RedisAuctionRealtimeStateReader.AuctionState state,
            Integer userId
    ) {
        CardSnapshot card = redisCardSnapshot(state);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(state.auctionId(), userId);
        List<AuctionResponses.AuctionPhoto> photos = java.util.stream.IntStream.range(0, state.imagePaths().size())
                .mapToObj(index -> new AuctionResponses.AuctionPhoto(null, state.imagePaths().get(index), index, index == 0))
                .toList();
        return AuctionResponses.AuctionDetail.builder()
                .id(state.auctionId()).card(cardSummary(card)).seller(sellerSummary(state.sellerId()))
                .startPrice(state.startPrice()).currentPrice(state.currentPrice()).bidIncrement(state.bidIncrement())
                .minimumBid(state.buyNowPrice() == null ? state.currentPrice() + state.bidIncrement()
                        : Math.min(state.currentPrice() + state.bidIncrement(), state.buyNowPrice()))
                .bidCount(state.bidCount()).startsAt(state.openTime()).endsAt(publicCloseTime(state)).status(state.status())
                .myBidStatus(realtime == null ? MyBidStatus.NONE : realtime.myBidStatus())
                .myBidAmount(realtime == null ? null : realtime.myBidAmount()).description(state.description())
                .sellerMemo(state.sellerMemo()).sellerGrade(state.selfGrade()).shippingFee(state.deliveryFee())
                .buyNowPrice(state.buyNowPrice()).photos(photos)
                .psaCertification(new AuctionResponses.PsaCertification(state.psaCertification(), card.psaGrade(), null,
                        state.psaVerified())).build();
    }

    private Instant publicCloseTime(RedisAuctionRealtimeStateReader.AuctionState state) {
        return state.status() == AuctionStatus.OPEN || state.status() == AuctionStatus.ENDING
                ? state.estimatedCloseTime()
                : state.closeTime();
    }

    private AuctionResponses.CardSummary cardSummary(CardSnapshot card) {
        return new AuctionResponses.CardSummary(
                card.cardId(),
                card.name(),
                card.setName(),
                card.psaGrade(),
                card.language(),
                card.thumbnailUrl()
        );
    }

    private AuctionResponses.SellerSummary sellerSummary(Integer sellerId) {
        return new AuctionResponses.SellerSummary(sellerId, "seller-" + sellerId, 0, 0);
    }

}
