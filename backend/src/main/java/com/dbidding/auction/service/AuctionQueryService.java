package com.dbidding.auction.service;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.card.service.CardService;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.auction.query.RedisAuctionRealtimeStateReader;
import com.dbidding.auction.bid.RedisAuctionStateSeeder;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class    AuctionQueryService {
    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final CardService cardService;
    private final AuctionCursorCodec auctionCursorCodec;
    private final Clock clock;
    @Autowired(required = false)
    private RedisAuctionRealtimeStateReader realtimeStateReader;
    @Autowired(required = false)
    private RedisAuctionStateSeeder stateSeeder;

    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            Integer userId,
            AuctionSearchRequest request
    ) {
        if (realtimeStateReader != null && realtimeStateReader.activeAuctionIds() != null) {
            return searchRedisActiveAuctions(userId, request);
        }
        var sort = request.sortOrDefault();
        AuctionCursor cursor = request.cursor() == null || request.cursor().isBlank()
                ? null
                : auctionCursorCodec.decode(request.cursor(), sort);
        int size = request.sizeOrDefault();
        List<Auction> fetched = auctionRepository.searchByCursor(
                request.keywordOrDefault(),
                request.psaGrade(),
                request.statusesOrDefault(),
                sort.name(),
                bidCountCursor(cursor),
                priceCursor(cursor),
                changeRateCursor(cursor),
                openTimeCursor(cursor),
                closeTimeCursor(cursor),
                cursor == null ? null : cursor.auctionId(),
                activeOnly(request),
                clock.instant(),
                PageRequest.of(0, size + 1)
        );
        boolean hasNext = fetched.size() > size;
        List<Auction> content = hasNext ? List.copyOf(fetched.subList(0, size)) : fetched;
        Map<Integer, CardSnapshot> cards = cardSnapshots(content);
        Map<Integer, List<AuctionImage>> images = imagesByAuction(content);
        Map<Integer, Bid> myBids = myBids(userId, content);
        List<AuctionResponses.AuctionSummary> items = content.stream()
                .map(auction -> summary(auction, cards.get(auction.getItemId()), firstImage(images, auction), myBids.get(auction.getId()),
                        realtimeStateReader == null ? null : realtimeStateReader.readSnapshot(auction.getId())))
                .toList();
        String nextCursor = hasNext
                ? auctionCursorCodec.encode(cursorOf(content.getLast(), sort))
                : null;
        return new AuctionResponses.CursorPage<>(
                items,
                nextCursor,
                hasNext
        );
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
        for (int batch = 0; collected.size() < limit && !exhausted && batch < SORT_ZSET_MAX_BATCHES; batch++) {
            List<ZSetOperations.TypedTuple<String>> raw = realtimeStateReader.activeIdsBatch(zsetKey, descending, bound, withinBoundOffset, SORT_ZSET_FETCH_BATCH_SIZE);
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
                            || request.psaGrade().equalsIgnoreCase(state.cardPsaGrade()))
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
            if (raw.size() < SORT_ZSET_FETCH_BATCH_SIZE) exhausted = true;
        }
        return collected.size() > limit ? collected.subList(0, limit) : collected;
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
            case ENDING_SOON -> java.util.Comparator.comparing(RedisAuctionRealtimeStateReader.AuctionState::closeTime)
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
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(state.auctionId(), userId);
        return AuctionResponses.AuctionSummary.builder()
                .id(state.auctionId()).card(cardSummary(card, null)).seller(sellerSummary(state.sellerId()))
                .startPrice(state.startPrice()).currentPrice(state.currentPrice()).bidIncrement(state.bidIncrement())
                .minimumBid(state.buyNowPrice() == null ? state.currentPrice() + state.bidIncrement()
                        : Math.min(state.currentPrice() + state.bidIncrement(), state.buyNowPrice()))
                .bidCount(state.bidCount()).buyNowPrice(state.buyNowPrice()).startsAt(state.openTime()).endsAt(publicCloseTime(state))
                .status(state.status()).myBidStatus(realtime == null ? MyBidStatus.NONE : realtime.myBidStatus())
                .myBidAmount(realtime == null ? null : realtime.myBidAmount()).build();
    }

    private CardSnapshot redisCardSnapshot(RedisAuctionRealtimeStateReader.AuctionState state) {
        return new CardSnapshot(
                state.itemId(), state.cardName(), state.cardSetName(), state.cardPsaGrade(), state.cardLanguage(), state.cardThumbnailUrl()
        );
    }

    public List<AuctionResponses.DashboardAuction> getDashboardAuctions(Integer userId) {
        Map<Integer, Bid> latestBids = new LinkedHashMap<>();
        bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(userId)
                .forEach(bid -> latestBids.putIfAbsent(bid.getAuction().getId(), bid));
        List<Auction> auctions = latestBids.values().stream().map(Bid::getAuction).distinct().toList();
        Map<Integer, CardSnapshot> cards = cardSnapshots(auctions);
        Map<Integer, List<AuctionImage>> images = imagesByAuction(auctions);
        return latestBids.values().stream()
                .map(bid -> dashboardAuction(bid, cards.get(bid.getAuction().getItemId()), firstImage(images, bid.getAuction())))
                .toList();
    }

    public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(Integer sellerId) {
        List<Auction> auctions = auctionRepository.findBySellerIdAndStatusOrderByCloseTimeDesc(
                sellerId, AuctionStatus.FAILED
        );
        Map<Integer, CardSnapshot> cards = cardSnapshots(auctions);
        return auctions.stream()
                .map(auction -> new AuctionResponses.FailedAuctionSummary(
                        auction.getId(),
                        cards.get(auction.getItemId()).name(),
                        auction.getStartPrice(),
                        auction.getCloseTime()
                ))
                .toList();
    }

    private Integer bidCountCursor(AuctionCursor cursor) {
        return cursor != null && cursor.sort() == AuctionSort.BID_COUNT
                ? Math.toIntExact(cursor.value())
                : null;
    }

    private Long priceCursor(AuctionCursor cursor) {
        return cursor != null && (cursor.sort() == AuctionSort.PRICE_HIGH
                || cursor.sort() == AuctionSort.PRICE_LOW)
                ? cursor.value()
                : null;
    }

    private Instant openTimeCursor(AuctionCursor cursor) {
        return cursor != null && cursor.sort() == AuctionSort.LATEST
                ? cursor.timeValue()
                : null;
    }

    private Instant closeTimeCursor(AuctionCursor cursor) {
        return cursor != null && cursor.sort() == AuctionSort.ENDING_SOON ? cursor.timeValue() : null;
    }

    private Long changeRateCursor(AuctionCursor cursor) {
        return cursor != null && cursor.sort() == AuctionSort.CHANGE_HIGH
                ? cursor.value()
                : null;
    }

    private AuctionCursor cursorOf(Auction auction, AuctionSort sort) {
        Long value = switch (sort) {
            case LATEST -> null;
            case BID_COUNT -> auction.getBidCount().longValue();
            case PRICE_HIGH, PRICE_LOW -> auction.getCurrentPrice();
            case CHANGE_HIGH -> auction.getChangeRateBasisPoints();
            case ENDING_SOON -> null;
        };
        Instant timeValue = sort == AuctionSort.LATEST ? auction.getOpenTime()
                : sort == AuctionSort.ENDING_SOON ? auction.getCloseTime() : null;
        return new AuctionCursor(sort, value, timeValue, auction.getId());
    }

    private boolean activeOnly(AuctionSearchRequest request) {
        return request.status() == null
                || request.status() == AuctionStatus.OPEN
                || request.status() == AuctionStatus.ENDING;
    }

    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        seedAuctionIfRequired(auctionId);
        RedisAuctionRealtimeStateReader.AuctionState redisState = realtimeStateReader == null ? null
                : realtimeStateReader.readAuctionState(auctionId);
        if (redisState != null) {
            return redisDetail(redisState, userId);
        }
        Auction auction = getAuction(auctionId);
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        List<AuctionImage> images = auctionImageRepository.findByAuctionIdOrderById(auction.getId());
        Bid myBid = currentUserBid(userId, auction.getId()).orElse(null);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader == null ? null
                : realtimeStateReader.read(auctionId, userId);
        return realtime == null ? detail(auction, card, images, myBid) : detail(auction, card, images, realtime);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
        if (realtimeStateReader != null && realtimeStateReader.readAuctionState(auctionId) != null) {
            RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader.read(auctionId, null);
            if (realtime == null) throw AuctionException.notFound();
            List<BidResponses.BidSummary> content = realtime.recentBids();
            return new AuctionResponses.Page<>(content, 0, request.sizeOrDefault(), content.size(), false);
        }
        Auction auction = getAuction(auctionId);
        Page<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(
                auction.getId(),
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault())
        );
        Optional<Bid> highestBid = highestBid(auction.getId());
        List<BidResponses.BidSummary> items = bids.getContent().stream()
                .map(bid -> bidSummary(bid, highestBid.map(Bid::getId).orElse(null)))
                .toList();
        return new AuctionResponses.Page<>(
                items,
                bids.getNumber(),
                bids.getSize(),
                bids.getTotalElements(),
                bids.hasNext()
        );
    }

    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        seedAuctionIfRequired(auctionId);
        WalletBalanceResponse wallet = walletService.getBalance(userId);
        RedisAuctionRealtimeStateReader.RealtimeState realtime = realtimeStateReader == null ? null : realtimeStateReader.read(auctionId, userId);
        if (realtime != null) {
            return BidResponses.BidContext.builder()
                    .auctionId(auctionId).status(realtime.status()).currentPrice(realtime.currentPrice())
                    .minimumBid(realtime.currentPrice() + realtime.bidIncrement()).bidIncrement(realtime.bidIncrement())
                    .myBidStatus(realtime.myBidStatus()).myBidAmount(realtime.myBidAmount())
                    .wallet(new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance()))
                    .recentBids(realtime.recentBids()).build();
        }
        Auction auction = getAuction(auctionId);
        Bid myBid = currentUserBid(userId, auction.getId()).orElse(null);
        var recentBids = getBids(auctionId, new PageRequestDto(0, 5)).content();
        return BidResponses.BidContext.builder()
                .auctionId(auction.getId())
                .status(auction.getStatus())
                .currentPrice(auction.getCurrentPrice())
                .minimumBid(auction.minimumBid())
                .bidIncrement(auction.getBidPriceUnit())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .wallet(new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance()))
                .recentBids(recentBids)
                .build();
    }

    private Auction getAuction(Integer auctionId) {
        return auctionRepository.findById(auctionId)
				.orElseThrow(AuctionException::notFound);
    }

    private void seedAuctionIfRequired(Integer auctionId) {
        if (realtimeStateReader != null && realtimeStateReader.readAuctionState(auctionId) == null && stateSeeder != null) {
            stateSeeder.seedIfAbsent(auctionId);
        }
    }

    private Map<Integer, CardSnapshot> cardSnapshots(List<Auction> auctions) {
        List<Integer> itemIds = auctions.stream().map(Auction::getItemId).distinct().toList();
        return itemIds.isEmpty() ? Map.of() : cardService.getCardSnapshots(itemIds);
    }

    private Map<Integer, List<AuctionImage>> imagesByAuction(List<Auction> auctions) {
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        return auctionImageRepository.findByAuctionIdInOrderById(auctionIds).stream()
                .collect(Collectors.groupingBy(image -> image.getAuction().getId()));
    }

    private Map<Integer, Bid> myBids(Integer userId, List<Auction> auctions) {
        if (userId == null) {
            return Map.of();
        }
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Bid> result = new HashMap<>();
        bidRepository.findByAuctionIdInAndBidderIdOrderByCreatedAtDescIdDesc(auctionIds, userId)
                .forEach(bid -> result.putIfAbsent(bid.getAuction().getId(), bid));
        return result;
    }

    private Optional<Bid> currentUserBid(Integer userId, Integer auctionId) {
        if (userId == null) {
            return Optional.empty();
        }
        return bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(auctionId, userId);
    }

    private Optional<Bid> highestBid(Integer auctionId) {
        return bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                auctionId,
                List.of(BidStatus.LEADING, BidStatus.WON)
        );
    }

    private AuctionImage firstImage(Map<Integer, List<AuctionImage>> images, Auction auction) {
        return images.getOrDefault(auction.getId(), List.of()).stream().findFirst().orElse(null);
    }

    private AuctionResponses.AuctionSummary summary(
            Auction auction,
            CardSnapshot card,
            AuctionImage representativeImage,
            Bid myBid,
            RedisAuctionRealtimeStateReader.Snapshot realtime
    ) {
        return AuctionResponses.AuctionSummary.builder()
                .id(auction.getId())
                .card(cardSummary(card, representativeImage))
                .seller(sellerSummary(auction.getSellerId()))
                .startPrice(auction.getStartPrice())
                .currentPrice(realtime == null ? auction.getCurrentPrice() : realtime.currentPrice())
                .bidIncrement(realtime == null ? auction.getBidPriceUnit() : realtime.bidIncrement())
                .minimumBid(realtime == null ? auction.minimumBid() : realtime.currentPrice() + realtime.bidIncrement())
                .bidCount(realtime == null ? auction.getBidCount() : realtime.bidCount())
                .buyNowPrice(realtime == null ? auction.getBuyNowPrice() : realtime.buyNowPrice())
                .startsAt(auction.getOpenTime())
                .endsAt(realtime == null ? publicCloseTime(auction) : publicCloseTime(auction, realtime.status(), realtime.closeTime()))
                .status(realtime == null ? auction.getStatus() : realtime.status())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .build();
    }

    private AuctionResponses.DashboardAuction dashboardAuction(
            Bid bid,
            CardSnapshot card,
            AuctionImage representativeImage
    ) {
        Auction auction = bid.getAuction();
        return new AuctionResponses.DashboardAuction(
                auction.getId(), auction.getSellerId(), cardSummary(card, representativeImage),
                auction.getStartPrice(), auction.getCurrentPrice(), auction.getBidPriceUnit(), auction.getBidCount(),
                auction.getEstimatedCloseTime(), auction.getCloseTime(), auction.getStatus(),
                bid.getStatus(), bid.getBidPrice()
        );
    }

    private Instant publicCloseTime(Auction auction) {
        return auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.ENDING
                ? auction.getEstimatedCloseTime() : auction.getCloseTime();
    }

    private AuctionResponses.AuctionDetail detail(
            Auction auction,
            CardSnapshot card,
            List<AuctionImage> images,
            Bid myBid
    ) {
        return AuctionResponses.AuctionDetail.builder()
                .id(auction.getId())
                .card(cardSummary(card, null))
                .seller(sellerSummary(auction.getSellerId()))
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .bidIncrement(auction.getBidPriceUnit())
                .minimumBid(auction.minimumBid())
                .bidCount(auction.getBidCount())
                .startsAt(auction.getOpenTime())
                .endsAt(publicCloseTime(auction))
                .status(auction.getStatus())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .description(auction.getDescription())
                .sellerMemo(auction.getSellerMemo())
                .sellerGrade(auction.getSelfGrade())
                .shippingFee(auction.getDeliveryFee())
                .buyNowPrice(auction.getBuyNowPrice())
                .photos(photos(images))
                .psaCertification(new AuctionResponses.PsaCertification(
                        auction.getPsaCertification(),
                        card.psaGrade(),
                        null,
                        Boolean.TRUE.equals(auction.getPsaVerified())
                ))
                .build();
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
                .id(state.auctionId()).card(cardSummary(card, null)).seller(sellerSummary(state.sellerId()))
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

    private AuctionResponses.AuctionDetail detail(
            Auction auction, CardSnapshot card, List<AuctionImage> images,
            RedisAuctionRealtimeStateReader.RealtimeState realtime
    ) {
        return AuctionResponses.AuctionDetail.builder()
                .id(auction.getId()).card(cardSummary(card, null)).seller(sellerSummary(auction.getSellerId()))
                .startPrice(auction.getStartPrice()).currentPrice(realtime.currentPrice())
                .bidIncrement(realtime.bidIncrement()).minimumBid(realtime.currentPrice() + realtime.bidIncrement())
                .bidCount(realtime.bidCount()).startsAt(auction.getOpenTime())
                .endsAt(publicCloseTime(auction, realtime.status(), realtime.closeTime()))
                .status(realtime.status()).myBidStatus(realtime.myBidStatus()).myBidAmount(realtime.myBidAmount())
                .description(auction.getDescription()).sellerMemo(auction.getSellerMemo()).sellerGrade(auction.getSelfGrade())
                .shippingFee(auction.getDeliveryFee()).buyNowPrice(realtime.buyNowPrice()).photos(photos(images))
                .psaCertification(new AuctionResponses.PsaCertification(auction.getPsaCertification(), card.psaGrade(), null,
                        Boolean.TRUE.equals(auction.getPsaVerified()))).build();
    }

    private Instant publicCloseTime(RedisAuctionRealtimeStateReader.AuctionState state) {
        return state.status() == AuctionStatus.OPEN || state.status() == AuctionStatus.ENDING
                ? state.estimatedCloseTime()
                : state.closeTime();
    }

    private Instant publicCloseTime(Auction auction, AuctionStatus realtimeStatus, Instant realtimeCloseTime) {
        return realtimeStatus == AuctionStatus.OPEN || realtimeStatus == AuctionStatus.ENDING
                ? auction.getEstimatedCloseTime()
                : realtimeCloseTime;
    }

    private boolean isVerifiedPsaCertification(String psaGrade, String psaCertification) {
        return psaGrade != null
                && psaGrade.trim().toUpperCase().startsWith("PSA")
                && psaCertification != null
                && psaCertification.matches("\\d{7,10}");
    }

    private AuctionResponses.CardSummary cardSummary(CardSnapshot card, AuctionImage representativeImage) {
        String thumbnailUrl = representativeImage == null ? card.thumbnailUrl() : representativeImage.getImagePath();
        return new AuctionResponses.CardSummary(
                card.cardId(),
                card.name(),
                card.setName(),
                card.psaGrade(),
                card.language(),
                thumbnailUrl
        );
    }

    private AuctionResponses.SellerSummary sellerSummary(Integer sellerId) {
        return new AuctionResponses.SellerSummary(sellerId, "seller-" + sellerId, 0, 0);
    }

    private List<AuctionResponses.AuctionPhoto> photos(List<AuctionImage> images) {
        return java.util.stream.IntStream.range(0, images.size())
                .mapToObj(index -> photo(images.get(index), index))
                .toList();
    }

    private AuctionResponses.AuctionPhoto photo(AuctionImage image, int order) {
        return new AuctionResponses.AuctionPhoto(
                image.getId(),
                image.getImagePath(),
                order,
                order == 0
        );
    }

    private BidResponses.BidSummary bidSummary(Bid bid, Long highestBidId) {
        return BidResponses.BidSummary.builder()
                .id(bid.getId())
                .amount(bid.getBidPrice())
                .bidderAlias(bidderAlias(bid.getBidderId()))
                .isHighest(Objects.equals(bid.getId(), highestBidId))
                .createdAt(bid.getCreatedAt())
                .build();
    }

    private String bidderAlias(Integer bidderId) {
        String value = String.valueOf(bidderId);
        if (value.length() <= 2) {
            return "user-" + value + "***";
        }
        return "user-" + value.substring(0, 2) + "***";
    }

    private MyBidStatus myBidStatus(Bid bid) {
        if (bid == null) {
            return MyBidStatus.NONE;
        }
        if (bid.getStatus() == BidStatus.LEADING || bid.getStatus() == BidStatus.WON) {
            return MyBidStatus.LEADING;
        }
        return MyBidStatus.OUTBID;
    }
}
