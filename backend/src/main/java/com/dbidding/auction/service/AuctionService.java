package com.dbidding.auction.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionSort;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.AuctionCreateResponse;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.AuctionCardStatisticPort;
import com.dbidding.auction.port.AuctionEventPort;
import com.dbidding.auction.port.CurrentUserPort;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Profile("auction-mock")
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionService {
    private static final int MAX_IMAGE_COUNT = 8;

    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final CurrentUserPort currentUserPort;
    private final WalletPort walletPort;
    private final ImageUploadPort imageUploadPort;
    private final AuctionCardPort auctionCardPort;
    private final AuctionCardStatisticPort auctionCardStatisticPort;
    private final AuctionEventPort auctionEventPort;
    private final Map<CreateIdempotencyKey, CachedAuctionCreate> createIdempotencyCache = new ConcurrentHashMap<>();

    @Transactional
    public AuctionCreateResponse create(AuctionCreateRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        var user = currentUserPort.currentUser();
        validateSeller(user);
        validateCreateRequest(request);

        CreateIdempotencyKey cacheKey = new CreateIdempotencyKey(user.id(), idempotencyKey);
        Optional<AuctionCreateResponse> cachedResponse = findCachedCreateResponse(cacheKey, request);
        if (cachedResponse.isPresent()) {
            return cachedResponse.get();
        }

        auctionCardPort.getCardSnapshot(request.itemId());
        List<ImageUploadPort.ResolvedImage> images = imageUploadPort.resolveImages(request.imageUploadTokens());
        validateImages(images);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsAt = now.plusHours(request.durationHours());
        Auction auction = auctionRepository.save(Auction.builder()
                .sellerId(user.id())
                .itemId(request.itemId())
                .auctionName(request.auctionName())
                .description(request.description())
                .startPrice(request.startPrice())
                .buyNowPrice(request.buyNowPrice())
                .deliveryFee(request.shippingFee())
                .openTime(now)
                .estimatedCloseTime(endsAt)
                .closeTime(endsAt)
                .bidPriceUnit(request.bidIncrement())
                .hyped(false)
                .build());
        List<AuctionImage> auctionImages = images.stream()
                .sorted(Comparator.comparingInt(ImageUploadPort.ResolvedImage::sortOrder))
                .map(image -> new AuctionImage(auction, image.imagePath()))
                .toList();
        auctionImageRepository.saveAll(auctionImages);
        auctionCardStatisticPort.recordAuctionOpened(auction.getItemId(), now);
        auctionEventPort.publish(new AuctionEventPort.AuctionEvent(
                AuctionEventPort.AuctionEventType.AUCTION_OPENED,
                auction.getId(),
                user.id(),
                request.startPrice()+request.shippingFee(),
                now
        ));

        AuctionCreateResponse response = AuctionCreateResponse.builder()
                .id(auction.getId())
                .status(auction.getStatus())
                .startsAt(auction.getOpenTime())
                .endsAt(auction.getEstimatedCloseTime())
                .version(auction.getVersion())
                .build();
        createIdempotencyCache.put(cacheKey, new CachedAuctionCreate(request, response));
        return response;
    }

    @Transactional
    public BidResponses.BidSummary participate(Integer auctionId, BidCreateRequest request) {
        var user = currentUserPort.currentUser();
        Auction auction = auctionRepository.findByIdForUpdate(auctionId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "존재하지 않는 경매입니다."));
        Bid previousLeadingBid = highestBid(auction.getId()).orElse(null);

        placeBid(auction, request.price());
        holdBidAmount(user.id(), auction.getId(), request.price());
        outbidPreviousLeadingBid(previousLeadingBid, user.id(), auction.getId());

        LocalDateTime now = LocalDateTime.now();
        Bid currentLeadingBid = bidRepository.save(Bid.leading(user.id(), auction, request.price(), now));
        auctionCardStatisticPort.recordBid(auction.getItemId(), now);
        publishBidPlaced(auction, user.id(), request.price(), now);
        return bidSummary(currentLeadingBid, currentLeadingBid.getId());
    }

    public AuctionResponses.Page<AuctionResponses.AuctionSummary> search(
            AuctionSearchRequest request
    ) {
        var auctions = auctionRepository.search(
                request.keywordOrDefault(),
                request.psaGrade(),
                request.statusOrDefault(),
                request.sortOrDefault().name(),
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault())
        );
        List<Auction> content = auctions.getContent();
        Map<Integer, AuctionCardPort.CardSnapshot> cards = cardSnapshots(content);
        Map<Integer, List<AuctionImage>> images = imagesByAuction(content);
        Map<Integer, Bid> myBids = myBids(content);
        List<AuctionResponses.AuctionSummary> items = content.stream()
                .map(auction -> summary(auction, cards.get(auction.getItemId()), firstImage(images, auction), myBids.get(auction.getId())))
                .toList();
        return new AuctionResponses.Page<>(
                items,
                auctions.getNumber(),
                auctions.getSize(),
                auctions.getTotalElements(),
                auctions.hasNext()
        );
    }

    public AuctionResponses.AuctionDetail getDetail(Integer auctionId) {
        Auction auction = getAuction(auctionId);
        AuctionCardPort.CardSnapshot card = auctionCardPort.getCardSnapshot(auction.getItemId());
        List<AuctionImage> images = auctionImageRepository.findByAuctionIdOrderById(auction.getId());
        Bid myBid = currentUserBid(auction.getId()).orElse(null);
        return detail(auction, card, images, myBid);
    }

    public AuctionResponses.Page<BidResponses.BidSummary> getBids(Integer auctionId, PageRequestDto request) {
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

    public BidResponses.BidContext getBidContext(Integer auctionId) {
        Auction auction = getAuction(auctionId);
        var user = currentUserPort.currentUser();
        WalletPort.WalletSnapshot wallet = walletPort.getWallet(user.id());
        Bid myBid = currentUserBid(auction.getId()).orElse(null);
        var recentBids = getBids(auctionId, new PageRequestDto(0, 5)).content();
        return BidResponses.BidContext.builder()
                .auctionId(auction.getId())
                .status(auction.getStatus())
                .version(auction.getVersion())
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
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "경매를 찾을 수 없습니다."));
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다.");
        }
    }

    private void validateSeller(CurrentUserPort.CurrentUser user) {
        if (!user.seller()) {
            throw new ResponseStatusException(FORBIDDEN, "판매자만 경매를 등록할 수 있습니다.");
        }
        if (user.restricted()) {
            throw new ResponseStatusException(FORBIDDEN, "제재된 사용자는 경매를 등록할 수 없습니다.");
        }
    }

    private void validateCreateRequest(AuctionCreateRequest request) {
        if (request.buyNowPrice() <= request.startPrice()) {
            throw new ResponseStatusException(BAD_REQUEST, "즉시구매가는 시작가보다 커야 합니다.");
        }
        if (request.imageUploadTokens().size() > MAX_IMAGE_COUNT) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지는 최대 8장까지 등록할 수 있습니다.");
        }
    }

    private Optional<AuctionCreateResponse> findCachedCreateResponse(
            CreateIdempotencyKey cacheKey,
            AuctionCreateRequest request
    ) {
        CachedAuctionCreate cached = createIdempotencyCache.get(cacheKey);
        if (cached == null) {
            return Optional.empty();
        }
        if (!cached.request().equals(request)) {
            throw new ResponseStatusException(CONFLICT, "같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
        }
        return Optional.of(cached.response());
    }

    private void validateImages(List<ImageUploadPort.ResolvedImage> images) {
        if (images.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지는 1장 이상 필요합니다.");
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지는 최대 8장까지 등록할 수 있습니다.");
        }
    }

    private void placeBid(Auction auction, Long price) {
        try {
            auction.placeBid(price);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void holdBidAmount(Integer bidderId, Integer auctionId, Long price) {
        try {
            walletPort.holdBidAmount(bidderId, auctionId, price);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void outbidPreviousLeadingBid(Bid previousLeadingBid, Integer currentBidderId, Integer auctionId) {
        if (previousLeadingBid == null) {
            return;
        }
        previousLeadingBid.markOutbid();
        auctionEventPort.publish(new AuctionEventPort.AuctionEvent(
                AuctionEventPort.AuctionEventType.BID_OUTBID,
                auctionId,
                previousLeadingBid.getBidderId(),
                previousLeadingBid.getBidPrice(),
                LocalDateTime.now()
        ));
        if (!previousLeadingBid.getBidderId().equals(currentBidderId)) {
            walletPort.releaseBidHold(previousLeadingBid.getBidderId(), auctionId);
        }
    }

    private void publishBidPlaced(Auction auction, Integer bidderId, Long bidPrice, LocalDateTime occurredAt) {
        auctionEventPort.publish(new AuctionEventPort.AuctionEvent(
                AuctionEventPort.AuctionEventType.BID_PLACED,
                auction.getId(),
                bidderId,
                bidPrice,
                occurredAt
        ));
    }

    private Map<Integer, AuctionCardPort.CardSnapshot> cardSnapshots(List<Auction> auctions) {
        List<Integer> itemIds = auctions.stream().map(Auction::getItemId).distinct().toList();
        return itemIds.isEmpty() ? Map.of() : auctionCardPort.getCardSnapshots(itemIds);
    }

    private Map<Integer, List<AuctionImage>> imagesByAuction(List<Auction> auctions) {
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        return auctionImageRepository.findByAuctionIdInOrderById(auctionIds).stream()
                .collect(Collectors.groupingBy(image -> image.getAuction().getId()));
    }

    private Map<Integer, Bid> myBids(List<Auction> auctions) {
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        Integer userId = currentUserPort.currentUser().id();
        Map<Integer, Bid> result = new HashMap<>();
        bidRepository.findByAuctionIdInAndBidderIdOrderByCreatedAtDesc(auctionIds, userId)
                .forEach(bid -> result.putIfAbsent(bid.getAuction().getId(), bid));
        return result;
    }

    private Optional<Bid> currentUserBid(Integer auctionId) {
        Integer userId = currentUserPort.currentUser().id();
        return bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDesc(auctionId, userId);
    }

    private Optional<Bid> highestBid(Integer auctionId) {
        return bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auctionId, BidStatus.LEADING);
    }

    private AuctionImage firstImage(Map<Integer, List<AuctionImage>> images, Auction auction) {
        return images.getOrDefault(auction.getId(), List.of()).stream().findFirst().orElse(null);
    }

    private AuctionResponses.AuctionSummary summary(
            Auction auction,
            AuctionCardPort.CardSnapshot card,
            AuctionImage representativeImage,
            Bid myBid
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
                .startsAt(auction.getOpenTime())
                .endsAt(auction.getEstimatedCloseTime())
                .status(auction.getStatus())
                .version(auction.getVersion())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .build();
    }

    private AuctionResponses.AuctionDetail detail(
            Auction auction,
            AuctionCardPort.CardSnapshot card,
            List<AuctionImage> images,
            Bid myBid
    ) {
        return AuctionResponses.AuctionDetail.builder()
                .id(auction.getId())
                .card(cardSummary(card, images.stream().findFirst().orElse(null)))
                .seller(sellerSummary(auction.getSellerId()))
                .startPrice(auction.getStartPrice())
                .currentPrice(auction.getCurrentPrice())
                .bidIncrement(auction.getBidPriceUnit())
                .minimumBid(auction.minimumBid())
                .bidCount(auction.getBidCount())
                .startsAt(auction.getOpenTime())
                .endsAt(auction.getEstimatedCloseTime())
                .status(auction.getStatus())
                .version(auction.getVersion())
                .myBidStatus(myBidStatus(myBid))
                .myBidAmount(myBid == null ? null : myBid.getBidPrice())
                .description(auction.getDescription())
                .sellerMemo(null)
                .shippingFee(auction.getDeliveryFee())
                .buyNowPrice(auction.getBuyNowPrice())
                .photos(photos(images))
                .psaCertification(new AuctionResponses.PsaCertification(null, card.psaGrade(), null, card.psaGrade() != null))
                .build();
    }

    private AuctionResponses.CardSummary cardSummary(AuctionCardPort.CardSnapshot card, AuctionImage representativeImage) {
        String thumbnailUrl = representativeImage == null ? card.thumbnailUrl() : representativeImage.getImagePath();
        return new AuctionResponses.CardSummary(
                card.itemId(),
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
        if (bid.getStatus() == BidStatus.LEADING) {
            return MyBidStatus.LEADING;
        }
        return MyBidStatus.OUTBID;
    }

    private record CreateIdempotencyKey(Integer userId, String idempotencyKey) {
    }

    private record CachedAuctionCreate(AuctionCreateRequest request, AuctionCreateResponse response) {
    }

}
