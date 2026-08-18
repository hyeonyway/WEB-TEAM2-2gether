package com.dbidding.auction.service.redis;

import com.dbidding.auction.bid.redis.RedisAuctionStateSeeder;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

/**
 * {@code AuctionQueryService}(파사드)가 Redis 실시간 상태를 우선 조회할 때 위임하는 구현체.
 * {@code redis} 프로필에서만 등록되며, 특정 경매가 Redis 실시간 상태에 없으면(오래된
 * seed-out 경매 등) 각 메서드가 {@code null}을 반환해 파사드가 {@link
 * com.dbidding.auction.service.dblock.DbAuctionQueryService}로 폴백하도록 한다.
 */
@Service
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionQueryService {
    private static final int SORT_ZSET_FETCH_BATCH_SIZE = 50;
    private static final int SORT_ZSET_MAX_BATCHES = 20;

    private final WalletService walletService;
    private final AuctionCursorCodec auctionCursorCodec;
    private final RedisAuctionRealtimeStateReader realtimeStateReader;
    private final RedisAuctionStateSeeder stateSeeder;

    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            Integer userId,
            AuctionSearchRequest request
    ) {
        AuctionSort sort = request.sortOrDefault();
        int size = request.sizeOrDefault();
        AuctionCursor cursor = request.cursor() == null || request.cursor().isBlank() ? null : auctionCursorCodec.decode(request.cursor(), sort);
        List<RedisAuctionRealtimeStateReader.AuctionState> page = fetchRedisSortedPage(request, sort, cursor, size + 1);
        boolean hasNext = page.size() > size;
        List<RedisAuctionRealtimeStateReader.AuctionState> content = hasNext ? page.subList(0, size) : page;
        Map<Integer, RedisAuctionRealtimeStateReader.MyBidState> myBids = realtimeStateReader.readMyBidStates(
                content.stream().map(RedisAuctionRealtimeStateReader.AuctionState::auctionId).toList(), userId
        );
        List<AuctionResponses.AuctionSummary> items = content.stream()
                .map(state -> redisSummary(state, myBids.get(state.auctionId())))
                .toList();
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
        // ENDING_SOON은 ENDING 그룹을 OPEN 그룹보다 먼저 보여주므로 closeTime만으로
        // Redis 범위를 좁히면 그룹 경계를 넘을 때 OPEN 항목을 건너뛴다. 이 정렬은
        // 활성 인덱스를 처음부터 읽고 아래의 그룹-aware cursor 필터로 이어간다.
        Double initialBound = cursor == null ? null : cursorScore(cursor, sort);
        Double bound = initialBound;
        long withinBoundOffset = 0;
        List<RedisAuctionRealtimeStateReader.AuctionState> collected = new ArrayList<>();
        boolean exhausted = false;
        // 동일 score 내부의 Redis member 순서와 숫자 auctionId tie-break 순서가 다를 수 있다.
        // 작은 페이지 크기로 조회하면 한 페이지를 채우기 위해 내부 배치를 과도하게 스캔하다가
        // SORT_ZSET_MAX_BATCHES에 도달해 전체 목록을 누락시킬 수 있으므로 고정 배치를 사용한다.
        int fetchBatchSize = SORT_ZSET_FETCH_BATCH_SIZE;
        for (int batch = 0; collected.size() < limit && !exhausted && batch < SORT_ZSET_MAX_BATCHES; batch++) {
            List<ZSetOperations.TypedTuple<String>> raw = realtimeStateReader.activeIdsBatch(zsetKey, descending, bound, withinBoundOffset, fetchBatchSize);
            if (raw.isEmpty()) {
                exhausted = true;
                break;
            }
            // 커서 경계 필터는 "아직 사용자 커서와 같은 score(동점 구간) 안에 있을 때"만 적용한다.
            // score가 바뀌면 그 뒤로는 전부 커서 이후이므로(순서상 모호함이 없음) 더 적용할 필요가 없다.
            AuctionCursor cursorForFilter = cursor != null && Objects.equals(bound, initialBound) ? cursor : null;
            List<Integer> rawIds = raw.stream().map(tuple -> Integer.valueOf(tuple.getValue())).toList();
            Map<Integer, RedisAuctionRealtimeStateReader.AuctionState> states = realtimeStateReader.readAuctionStates(rawIds);
            List<RedisAuctionRealtimeStateReader.AuctionState> filtered = rawIds.stream()
                    .map(states::get)
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
            long itemsAtLastScore = raw.stream().filter(tuple -> Double.compare(tuple.getScore(), lastScore) == 0).count();
            if (bound != null && Double.compare(lastScore, bound) == 0) {
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
            return sort == AuctionSort.ENDING_SOON ? state.auctionId() > cursor.auctionId() : state.auctionId() < cursor.auctionId();
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
                            RedisAuctionRealtimeStateReader.AuctionState::closeTime)
                    .thenComparing(RedisAuctionRealtimeStateReader.AuctionState::auctionId);
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

    private AuctionResponses.AuctionSummary redisSummary(
            RedisAuctionRealtimeStateReader.AuctionState state,
            RedisAuctionRealtimeStateReader.MyBidState myBid
    ) {
        CardSnapshot card = redisCardSnapshot(state);
        return AuctionResponses.AuctionSummary.builder()
                .id(state.auctionId()).card(cardSummary(card)).seller(sellerSummary(state.sellerId()))
                .startPrice(state.startPrice()).currentPrice(state.currentPrice()).bidIncrement(state.bidIncrement())
                .minimumBid(state.buyNowPrice() == null ? state.currentPrice() + state.bidIncrement()
                        : Math.min(state.currentPrice() + state.bidIncrement(), state.buyNowPrice()))
                .bidCount(state.bidCount()).buyNowPrice(state.buyNowPrice()).startsAt(state.openTime()).endsAt(publicCloseTime(state))
                .status(state.status()).myBidStatus(myBid == null ? MyBidStatus.NONE : myBid.status())
                .myBidAmount(myBid == null ? null : myBid.amount()).build();
    }

    private CardSnapshot redisCardSnapshot(RedisAuctionRealtimeStateReader.AuctionState state) {
        return new CardSnapshot(
                state.itemId(), state.cardName(), state.cardSetName(), state.cardPsaGrade(), state.cardLanguage(), state.cardThumbnailUrl()
        );
    }

    /** Redis 실시간 상태에 없으면(오래된 seed-out 경매 등) {@code null} — 호출자는 DB로 폴백한다. */
    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        RedisAuctionRealtimeStateReader.StoredAuctionState stored = seedAndReadIfRequired(auctionId);
        return stored == null ? null : redisDetail(stored, userId);
    }

    /** Redis 실시간 상태에 없으면 {@code null} — 호출자는 DB로 폴백한다. */
    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
        RedisAuctionRealtimeStateReader.StoredAuctionState stored = realtimeStateReader.readStoredAuctionState(auctionId);
        if (stored == null) {
            return null;
        }
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(stored, null);
        if (realtime == null) throw AuctionException.notFound();
        List<BidResponses.BidSummary> content = realtime.recentBids();
        return new AuctionResponses.Page<>(content, 0, request.sizeOrDefault(), content.size(), false);
    }

    /**
     * Redis 실시간 상태에 없으면 {@code null} — 호출자(파사드)는 DB로 폴백한다. Redis 조회가
     * 실패(예외)하면 지갑 조회 전에 그대로 전파한다 — cache miss처럼 DB로 폴백하지 않는다.
     */
    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        RedisAuctionRealtimeStateReader.StoredAuctionState stored = realtimeStateReader.readStoredAuctionState(auctionId);
        if (stored == null && stateSeeder != null) {
            stateSeeder.seedIfAbsent(auctionId);
            stored = realtimeStateReader.readStoredAuctionState(auctionId);
        }
        WalletBalanceResponse wallet = walletService.getBalance(userId);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = stored == null
                ? null : realtimeStateReader.read(stored, userId);
        if (realtime == null) {
            return null;
        }
        return BidResponses.BidContext.builder()
                .auctionId(auctionId).status(realtime.status()).currentPrice(realtime.currentPrice())
                .minimumBid(realtime.currentPrice() + realtime.bidIncrement()).bidIncrement(realtime.bidIncrement())
                .buyNowPrice(realtime.buyNowPrice())
                .myBidStatus(realtime.myBidStatus()).myBidAmount(realtime.myBidAmount())
                .wallet(new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance()))
                .recentBids(realtime.recentBids()).build();
    }

    private RedisAuctionRealtimeStateReader.StoredAuctionState seedAndReadIfRequired(Integer auctionId) {
        RedisAuctionRealtimeStateReader.StoredAuctionState stored = realtimeStateReader.readStoredAuctionState(auctionId);
        if (stored == null && stateSeeder != null) {
            stateSeeder.seedIfAbsent(auctionId);
            stored = realtimeStateReader.readStoredAuctionState(auctionId);
        }
        return stored;
    }

    private AuctionResponses.AuctionDetail redisDetail(
            RedisAuctionRealtimeStateReader.StoredAuctionState stored,
            Integer userId
    ) {
        RedisAuctionRealtimeStateReader.AuctionState state = stored.state();
        CardSnapshot card = redisCardSnapshot(state);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(stored, userId);
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
