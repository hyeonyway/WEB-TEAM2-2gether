package com.dbidding.auction.bid.redis;

import com.dbidding.auction.bid.dto.AuctionSeedData;
import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/** MySQL projection의 활성 경매를 Redis state miss 때만 조건부 생성한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionStateSeeder {
    private static final String ACTIVE_BY_CLOSE_TIME = "auction:active:by-close-time";
    private static final String ACTIVE_BY_BID_COUNT = "auction:active:by-bid-count";
    private static final String ACTIVE_BY_PRICE = "auction:active:by-price";
    private static final String ACTIVE_BY_CHANGE_RATE = "auction:active:by-change-rate";
    private static final String ACTIVE_BY_OPEN_TIME = "auction:active:by-open-time";
    private final StringRedisTemplate redisTemplate;
    private final RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    private final RedisStateSingleFlight singleFlight;
    private final RedisAuctionSeedBatchCoordinator batchCoordinator;
    @Qualifier("auctionStateSeedScript") private final RedisScript<Long> auctionStateSeedScript;

    public boolean seedIfAbsent(Integer auctionId) {
        String key = "auction:state:" + auctionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return false;
        return singleFlight.execute(key, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return false;
            if (!projectionCatchUpVerifier.isCaughtUpForAuctionFresh(auctionId)) throw AuctionException.stateRecoveryRequired();
            return batchCoordinator.requestSeedData(auctionId).join()
                    .map(data -> seed(data.auction(), data.leading(), data.card(), data.imagePaths(), data.latestBids(), data.recentBids()))
                    .orElse(false);
        });
    }

    /** @return warm-up된 경매들의 현재 낙찰 후보(HELD 지갑을 가진) userId 목록 - 지갑 warm-up 범위 결정에 사용 */
    public List<Integer> seedAllIfAbsent(List<Auction> auctions) {
        if (!projectionCatchUpVerifier.isCaughtUp()) return List.of();
        List<Auction> active = auctions.stream().filter(auction -> EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING).contains(auction.getStatus())).toList();
        if (active.isEmpty()) return List.of();
        List<CompletableFuture<Optional<AuctionSeedData>>> futures = active.stream()
                .map(Auction::getId)
                .map(batchCoordinator::requestSeedData)
                .toList();
        List<AuctionSeedData> seedData = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(Optional::stream)
                .toList();
        seedData.forEach(data -> seed(
                data.auction(), data.leading(), data.card(), data.imagePaths(),
                data.latestBids(), data.recentBids()));
        return seedData.stream()
                .map(AuctionSeedData::leading)
                .filter(Objects::nonNull)
                .map(Bid::getBidderId)
                .distinct()
                .toList();
    }

    private boolean seed(Auction auction, Bid leading, CardSnapshot card, List<String> imagePathList, List<Bid> latestBids, List<Bid> recentBids) {
        String imagePaths = String.join("\n", imagePathList);
        List<String> stateArgs = new ArrayList<>();
        put(stateArgs, "status", auction.getStatus().name()); put(stateArgs, "sellerId", auction.getSellerId()); put(stateArgs, "itemId", auction.getItemId());
        put(stateArgs, "cardName", card.name()); put(stateArgs, "cardSetName", card.setName()); put(stateArgs, "cardPsaGrade", nullToEmpty(card.psaGrade())); put(stateArgs, "cardLanguage", nullToEmpty(card.language())); put(stateArgs, "cardThumbnailUrl", card.thumbnailUrl());
        put(stateArgs, "auctionName", auction.getAuctionName()); put(stateArgs, "description", auction.getDescription()); put(stateArgs, "sellerMemo", nullToEmpty(auction.getSellerMemo()));
        put(stateArgs, "psaCertification", nullToEmpty(auction.getPsaCertification())); put(stateArgs, "selfGrade", nullToEmpty(auction.getSelfGrade())); put(stateArgs, "psaVerified", auction.getPsaVerified());
        put(stateArgs, "startPrice", auction.getStartPrice()); put(stateArgs, "currentPrice", auction.getCurrentPrice()); put(stateArgs, "buyNowPrice", auction.getBuyNowPrice() == null ? "" : auction.getBuyNowPrice());
        put(stateArgs, "deliveryFee", auction.getDeliveryFee()); put(stateArgs, "bidIncrement", auction.getBidPriceUnit()); put(stateArgs, "imagePaths", imagePaths);
        put(stateArgs, "openTime", auction.getOpenTime()); put(stateArgs, "closeTime", auction.getCloseTime()); put(stateArgs, "closeTimeEpochMillis", auction.getCloseTime().toEpochMilli());
        put(stateArgs, "estimatedCloseTime", auction.getEstimatedCloseTime()); put(stateArgs, "estimatedCloseTimeEpochMillis", auction.getEstimatedCloseTime().toEpochMilli());
        put(stateArgs, "highestBidderId", leading == null ? "" : leading.getBidderId()); put(stateArgs, "highestHoldAmount", leading == null ? 0 : leading.getBidPrice());
        // bidCount에는 Redis Stream 도입 전의 입찰 이력도 포함될 수 있다. 이벤트 버전은
        // MySQL projection이 마지막으로 반영한 버전에서 이어야 하므로 별도로 초기화한다.
        put(stateArgs, "sequence", auction.getLastBidEventVersion()); put(stateArgs, "bidCount", auction.getBidCount());
        List<String> args = new ArrayList<>(List.of(String.valueOf(auction.getCloseTime().toEpochMilli()), String.valueOf(auction.getId()), String.valueOf(stateArgs.size() / 2)));
        args.addAll(stateArgs);
        args.add(String.valueOf(latestBids.size()));
        latestBids.forEach(bid -> { args.add(String.valueOf(bid.getBidderId())); args.add(redisBidStatus(bid)); args.add(String.valueOf(bid.getBidPrice())); });
        List<Bid> chronologicalRecentBids = recentBids.stream().sorted(Comparator.comparing(Bid::getCreatedAt).thenComparing(Bid::getId)).toList();
        args.add(String.valueOf(chronologicalRecentBids.size()));
        chronologicalRecentBids.forEach(bid -> { args.add(String.valueOf(bid.getId())); args.add(String.valueOf(bid.getBidderId())); args.add(String.valueOf(bid.getBidPrice())); args.add(String.valueOf(bid.getId())); args.add(bid.getCreatedAt().toString()); });
        args.add(String.valueOf(auction.getBidCount()));
        args.add(String.valueOf(auction.getCurrentPrice()));
        args.add(String.valueOf(changeRateBasisPoints(auction)));
        args.add(String.valueOf(auction.getOpenTime().toEpochMilli()));
        return Long.valueOf(1L).equals(redisTemplate.execute(auctionStateSeedScript,
                List.of("auction:state:" + auction.getId(), ACTIVE_BY_CLOSE_TIME, "auction:recent-bids:" + auction.getId(), "auction:ending-window:by-close-time",
                        ACTIVE_BY_BID_COUNT, ACTIVE_BY_PRICE, ACTIVE_BY_CHANGE_RATE, ACTIVE_BY_OPEN_TIME),
                args.toArray()));
    }

    private long changeRateBasisPoints(Auction auction) {
        return (auction.getCurrentPrice() - auction.getStartPrice()) * 10_000L / auction.getStartPrice();
    }

    private String redisBidStatus(Bid bid) { return bid.getStatus() == BidStatus.LEADING || bid.getStatus() == BidStatus.WON ? "LEADING" : "OUTBID"; }

    private void put(List<String> args, String field, Object value) { args.add(field); args.add(String.valueOf(value)); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
