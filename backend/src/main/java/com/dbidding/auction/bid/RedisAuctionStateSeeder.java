package com.dbidding.auction.bid;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.stream.RedisProjectionCatchUpVerifier;
import com.dbidding.global.concurrent.RedisStateSingleFlight;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** MySQL projection의 활성 경매를 Redis state miss 때만 조건부 생성한다. */
@Component
@Profile("redis")
@RequiredArgsConstructor
public class RedisAuctionStateSeeder {
    private static final String ACTIVE_BY_CLOSE_TIME = "auction:active:by-close-time";
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final RedisCardStateReader cardStateReader;
    private final StringRedisTemplate redisTemplate;
    private final RedisProjectionCatchUpVerifier projectionCatchUpVerifier;
    private final RedisStateSingleFlight singleFlight;
    @Qualifier("auctionStateSeedScript") private final RedisScript<Long> auctionStateSeedScript;

    @Transactional(readOnly = true)
    public boolean seedIfAbsent(Integer auctionId) {
        String key = "auction:state:" + auctionId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return false;
        return singleFlight.execute(key, () -> {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) return false;
            if (!projectionCatchUpVerifier.isCaughtUp()) throw AuctionException.stateRecoveryRequired();
            return auctionRepository.findByIdAndStatusNot(auctionId, AuctionStatus.ENDED)
                    .filter(auction -> EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING).contains(auction.getStatus()))
                    .map(this::seed).orElse(false);
        });
    }

    @Transactional(readOnly = true)
    public void seedAllIfAbsent(List<Auction> auctions) {
        if (!projectionCatchUpVerifier.isCaughtUp()) return;
        List<Auction> active = auctions.stream().filter(auction -> EnumSet.of(AuctionStatus.OPEN, AuctionStatus.ENDING).contains(auction.getStatus())).toList();
        if (active.isEmpty()) return;
        List<Integer> auctionIds = active.stream().map(Auction::getId).toList();
        java.util.Map<Integer, Bid> leading = bidRepository.findByAuctionIdInAndStatus(auctionIds, BidStatus.LEADING).stream()
                .collect(java.util.stream.Collectors.toMap(bid -> bid.getAuction().getId(), bid -> bid, (first, ignored) -> first));
        java.util.Map<Integer, CardSnapshot> cards = cardStateReader.getCardSnapshots(active.stream().map(Auction::getItemId).distinct().toList());
        java.util.Map<Integer, List<String>> imagePaths = auctionImageRepository.findByAuctionIdInOrderById(auctionIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(image -> image.getAuction().getId(), java.util.stream.Collectors.mapping(image -> image.getImagePath(), java.util.stream.Collectors.toList())));
        active.forEach(auction -> seed(auction, leading.get(auction.getId()), cards.get(auction.getItemId()), imagePaths.getOrDefault(auction.getId(), List.of())));
    }

    private boolean seed(Auction auction) {
        Bid leading = bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auction.getId(), BidStatus.LEADING).orElse(null);
        CardSnapshot card = cardStateReader.getCardSnapshot(auction.getItemId());
        return seed(auction, leading, card, auctionImageRepository.findByAuctionIdOrderById(auction.getId()).stream().map(image -> image.getImagePath()).toList());
    }

    private boolean seed(Auction auction, Bid leading, CardSnapshot card, List<String> imagePathList) {
        String imagePaths = String.join("\n", imagePathList);
        List<String> args = new ArrayList<>(List.of(String.valueOf(auction.getCloseTime().toEpochMilli()), String.valueOf(auction.getId())));
        put(args, "status", auction.getStatus().name()); put(args, "sellerId", auction.getSellerId()); put(args, "itemId", auction.getItemId());
        put(args, "cardName", card.name()); put(args, "cardSetName", card.setName()); put(args, "cardPsaGrade", nullToEmpty(card.psaGrade())); put(args, "cardLanguage", nullToEmpty(card.language())); put(args, "cardThumbnailUrl", card.thumbnailUrl());
        put(args, "auctionName", auction.getAuctionName()); put(args, "description", auction.getDescription()); put(args, "sellerMemo", nullToEmpty(auction.getSellerMemo()));
        put(args, "psaCertification", nullToEmpty(auction.getPsaCertification())); put(args, "selfGrade", nullToEmpty(auction.getSelfGrade())); put(args, "psaVerified", auction.getPsaVerified());
        put(args, "startPrice", auction.getStartPrice()); put(args, "currentPrice", auction.getCurrentPrice()); put(args, "buyNowPrice", auction.getBuyNowPrice() == null ? "" : auction.getBuyNowPrice());
        put(args, "deliveryFee", auction.getDeliveryFee()); put(args, "bidIncrement", auction.getBidPriceUnit()); put(args, "imagePaths", imagePaths);
        put(args, "openTime", auction.getOpenTime()); put(args, "closeTime", auction.getCloseTime()); put(args, "closeTimeEpochMillis", auction.getCloseTime().toEpochMilli());
        put(args, "highestBidderId", leading == null ? "" : leading.getBidderId()); put(args, "highestHoldAmount", leading == null ? 0 : leading.getBidPrice());
        // bidCount에는 Redis Stream 도입 전의 입찰 이력도 포함될 수 있다. 이벤트 버전은
        // MySQL projection이 마지막으로 반영한 버전에서 이어야 하므로 별도로 초기화한다.
        put(args, "sequence", auction.getLastBidEventVersion()); put(args, "bidCount", auction.getBidCount());
        return Long.valueOf(1L).equals(redisTemplate.execute(auctionStateSeedScript, List.of("auction:state:" + auction.getId(), ACTIVE_BY_CLOSE_TIME), args.toArray()));
    }

    private void put(List<String> args, String field, Object value) { args.add(field); args.add(String.valueOf(value)); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
}
