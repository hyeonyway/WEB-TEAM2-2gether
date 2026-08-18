package com.dbidding.auction.service.dblock;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionCursor;
import com.dbidding.auction.dto.AuctionCursorCodec;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** MySQL 조회와 entity 기반 응답 조립을 하나의 짧은 read-only 경계에서 완료한다. */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DbAuctionQueryService {
    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final CardService cardService;
    private final AuctionCursorCodec auctionCursorCodec;
    private final Clock clock;
    private final WalletService walletService;

    public AuctionResponses.CursorPage<AuctionResponses.AuctionSummary> search(
            Integer userId, AuctionSearchRequest request
    ) {
        AuctionSort sort = request.sortOrDefault();
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
                .map(auction -> summary(
                        auction,
                        cards.get(auction.getItemId()),
                        firstImage(images, auction),
                        myBids.get(auction.getId())))
                .toList();
        String nextCursor = hasNext
                ? auctionCursorCodec.encode(cursorOf(content.getLast(), sort))
                : null;
        return new AuctionResponses.CursorPage<>(items, nextCursor, hasNext);
    }

    public List<AuctionResponses.DashboardAuction> getDashboardAuctions(Integer userId) {
        Map<Integer, Bid> latestBids = new LinkedHashMap<>();
        bidRepository.findByBidderIdOrderByCreatedAtDescIdDesc(userId)
                .forEach(bid -> latestBids.putIfAbsent(bid.getAuction().getId(), bid));
        List<Auction> auctions = latestBids.values().stream().map(Bid::getAuction).distinct().toList();
        Map<Integer, CardSnapshot> cards = cardSnapshots(auctions);
        Map<Integer, List<AuctionImage>> images = imagesByAuction(auctions);
        return latestBids.values().stream()
                .map(bid -> dashboardAuction(
                        bid,
                        cards.get(bid.getAuction().getItemId()),
                        firstImage(images, bid.getAuction())))
                .toList();
    }

    public List<AuctionResponses.FailedAuctionSummary> getFailedAuctions(Integer sellerId) {
        List<Auction> auctions = auctionRepository.findBySellerIdAndStatusOrderByCloseTimeDesc(
                sellerId, AuctionStatus.FAILED);
        Map<Integer, CardSnapshot> cards = cardSnapshots(auctions);
        return auctions.stream()
                .map(auction -> new AuctionResponses.FailedAuctionSummary(
                        auction.getId(),
                        cards.get(auction.getItemId()).name(),
                        auction.getStartPrice(),
                        auction.getCloseTime()))
                .toList();
    }

    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        Auction auction = getAuction(auctionId);
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        List<AuctionImage> images = auctionImageRepository.findByAuctionIdOrderById(auctionId);
        Bid myBid = currentUserBid(userId, auctionId).orElse(null);
        return detail(auction, card, images, myBid);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(
            Integer auctionId, PageRequestDto request
    ) {
        Auction auction = getAuction(auctionId);
        Page<Bid> bids = bidRepository.findByAuctionIdOrderByCreatedAtDescIdDesc(
                auction.getId(), PageRequest.of(request.pageOrDefault(), request.sizeOrDefault()));
        Optional<Bid> highestBid = highestBid(auction.getId());
        List<BidResponses.BidSummary> items = bids.getContent().stream()
                .map(bid -> bidSummary(bid, highestBid.map(Bid::getId).orElse(null)))
                .toList();
        return new AuctionResponses.Page<>(
                items, bids.getNumber(), bids.getSize(), bids.getTotalElements(), bids.hasNext());
    }

    public BidResponses.BidContext getBidContext(
            Integer userId, Integer auctionId, WalletBalanceResponse wallet
    ) {
        Auction auction = getAuction(auctionId);
        Bid myBid = currentUserBid(userId, auctionId).orElse(null);
        List<BidResponses.BidSummary> recentBids = getBids(
                auctionId, new PageRequestDto(0, 5)).content();
        return BidResponses.BidContext.builder()
                .auctionId(auction.getId())
                .status(auction.getStatus())
                .currentPrice(auction.getCurrentPrice())
                .minimumBid(auction.minimumBid())
                .bidIncrement(auction.getBidPriceUnit())
                .buyNowPrice(auction.getBuyNowPrice())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .wallet(new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance()))
                .recentBids(recentBids)
                .build();
    }

    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        return getBidContext(userId, auctionId, walletService.getBalance(userId));
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
        return cursor != null && cursor.sort() == AuctionSort.LATEST ? cursor.timeValue() : null;
    }

    private Instant closeTimeCursor(AuctionCursor cursor) {
        return cursor != null && cursor.sort() == AuctionSort.ENDING_SOON ? cursor.timeValue() : null;
    }

    private Long changeRateCursor(AuctionCursor cursor) {
        return cursor != null && cursor.sort() == AuctionSort.CHANGE_HIGH ? cursor.value() : null;
    }

    private AuctionCursor cursorOf(Auction auction, AuctionSort sort) {
        Long value = switch (sort) {
            case LATEST, ENDING_SOON -> null;
            case BID_COUNT -> auction.getBidCount().longValue();
            case PRICE_HIGH, PRICE_LOW -> auction.getCurrentPrice();
            case CHANGE_HIGH -> auction.getChangeRateBasisPoints();
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

    private Auction getAuction(Integer auctionId) {
        return auctionRepository.findById(auctionId).orElseThrow(AuctionException::notFound);
    }

    private Map<Integer, CardSnapshot> cardSnapshots(List<Auction> auctions) {
        List<Integer> itemIds = auctions.stream().map(Auction::getItemId).distinct().toList();
        return itemIds.isEmpty() ? Map.of() : cardService.getCardSnapshots(itemIds);
    }

    private Map<Integer, List<AuctionImage>> imagesByAuction(List<Auction> auctions) {
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) return Map.of();
        return auctionImageRepository.findByAuctionIdInOrderById(auctionIds).stream()
                .collect(Collectors.groupingBy(image -> image.getAuction().getId()));
    }

    private Map<Integer, Bid> myBids(Integer userId, List<Auction> auctions) {
        if (userId == null) return Map.of();
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) return Map.of();
        Map<Integer, Bid> result = new HashMap<>();
        bidRepository.findByAuctionIdInAndBidderIdOrderByCreatedAtDescIdDesc(auctionIds, userId)
                .forEach(bid -> result.putIfAbsent(bid.getAuction().getId(), bid));
        return result;
    }

    private Optional<Bid> currentUserBid(Integer userId, Integer auctionId) {
        if (userId == null) return Optional.empty();
        return bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDescIdDesc(auctionId, userId);
    }

    private Optional<Bid> highestBid(Integer auctionId) {
        return bidRepository.findFirstByAuctionIdAndStatusInOrderByBidPriceDescCreatedAtAsc(
                auctionId, List.of(BidStatus.LEADING, BidStatus.WON));
    }

    private AuctionImage firstImage(Map<Integer, List<AuctionImage>> images, Auction auction) {
        return images.getOrDefault(auction.getId(), List.of()).stream().findFirst().orElse(null);
    }

    private AuctionResponses.AuctionSummary summary(
            Auction auction, CardSnapshot card, AuctionImage representativeImage, Bid myBid
    ) {
        return AuctionResponses.AuctionSummary.builder()
                .id(auction.getId())
                .card(cardSummary(card, representativeImage))
                .seller(sellerSummary(auction.getSellerId()))
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .bidIncrement(auction.getBidPriceUnit())
                .minimumBid(auction.minimumBid())
                .bidCount(auction.getBidCount())
                .buyNowPrice(auction.getBuyNowPrice())
                .startsAt(auction.getOpenTime())
                .endsAt(publicCloseTime(auction))
                .status(auction.getStatus())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .build();
    }

    private AuctionResponses.DashboardAuction dashboardAuction(
            Bid bid, CardSnapshot card, AuctionImage representativeImage
    ) {
        Auction auction = bid.getAuction();
        return new AuctionResponses.DashboardAuction(
                auction.getId(), auction.getSellerId(), cardSummary(card, representativeImage),
                auction.getStartPrice(), auction.getCurrentPrice(), auction.getBidPriceUnit(),
                auction.getBidCount(), auction.getEstimatedCloseTime(), auction.getCloseTime(),
                auction.getStatus(), bid.getStatus(), bid.getBidPrice());
    }

    private AuctionResponses.AuctionDetail detail(
            Auction auction, CardSnapshot card, List<AuctionImage> images, Bid myBid
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
                        auction.getPsaCertification(), card.psaGrade(), null,
                        Boolean.TRUE.equals(auction.getPsaVerified())))
                .build();
    }

    private Instant publicCloseTime(Auction auction) {
        return auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.ENDING
                ? auction.getEstimatedCloseTime()
                : auction.getCloseTime();
    }

    private AuctionResponses.CardSummary cardSummary(CardSnapshot card, AuctionImage representativeImage) {
        String thumbnailUrl = representativeImage == null
                ? card.thumbnailUrl()
                : representativeImage.getImagePath();
        return new AuctionResponses.CardSummary(
                card.cardId(), card.name(), card.setName(), card.psaGrade(), card.language(), thumbnailUrl);
    }

    private AuctionResponses.SellerSummary sellerSummary(Integer sellerId) {
        return new AuctionResponses.SellerSummary(sellerId, "seller-" + sellerId, 0, 0);
    }

    private List<AuctionResponses.AuctionPhoto> photos(List<AuctionImage> images) {
        return java.util.stream.IntStream.range(0, images.size())
                .mapToObj(index -> new AuctionResponses.AuctionPhoto(
                        images.get(index).getId(), images.get(index).getImagePath(), index, index == 0))
                .toList();
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
        return value.length() <= 2
                ? "user-" + value + "***"
                : "user-" + value.substring(0, 2) + "***";
    }

    private MyBidStatus myBidStatus(Bid bid) {
        if (bid == null) return MyBidStatus.NONE;
        return bid.getStatus() == BidStatus.LEADING || bid.getStatus() == BidStatus.WON
                ? MyBidStatus.LEADING
                : MyBidStatus.OUTBID;
    }
}
