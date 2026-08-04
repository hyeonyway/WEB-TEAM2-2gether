package com.dbidding.auction.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static com.dbidding.global.time.UtcTime.toInstant;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.domain.MyBidStatus;
import com.dbidding.auction.dto.AuctionResponses;
import com.dbidding.auction.dto.AuctionSearchRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.dto.PageRequestDto;
import com.dbidding.auction.port.AuctionCardPort;
import com.dbidding.auction.port.WalletPort;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.util.HashMap;
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
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AuctionQueryService {
    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final WalletPort walletPort;
    private final AuctionCardPort auctionCardPort;

    public AuctionResponses.Page<AuctionResponses.AuctionSummary> search(Integer userId, AuctionSearchRequest request) {
        var auctions = auctionRepository.search(
                request.keywordOrDefault(),
                request.psaGrade(),
                request.statusesOrDefault(),
                request.sortOrDefault().name(),
                PageRequest.of(request.pageOrDefault(), request.sizeOrDefault())
        );
        List<Auction> content = auctions.getContent();
        Map<Integer, AuctionCardPort.CardSnapshot> cards = cardSnapshots(content);
        Map<Integer, List<AuctionImage>> images = imagesByAuction(content);
        Map<Integer, Bid> myBids = myBids(userId, content);
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

    public AuctionResponses.AuctionDetail getDetail(Integer userId, Integer auctionId) {
        Auction auction = getAuction(auctionId);
        AuctionCardPort.CardSnapshot card = auctionCardPort.getCardSnapshot(auction.getItemId());
        List<AuctionImage> images = auctionImageRepository.findByAuctionIdOrderById(auction.getId());
        Bid myBid = currentUserBid(userId, auction.getId()).orElse(null);
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

    public BidResponses.BidContext getBidContext(Integer userId, Integer auctionId) {
        Auction auction = getAuction(auctionId);
        WalletPort.WalletSnapshot wallet = walletPort.getWallet(userId);
        Bid myBid = currentUserBid(userId, auction.getId()).orElse(null);
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

    private Map<Integer, Bid> myBids(Integer userId, List<Auction> auctions) {
        if (userId == null) {
            return Map.of();
        }
        List<Integer> auctionIds = auctions.stream().map(Auction::getId).toList();
        if (auctionIds.isEmpty()) {
            return Map.of();
        }
        Map<Integer, Bid> result = new HashMap<>();
        bidRepository.findByAuctionIdInAndBidderIdOrderByCreatedAtDesc(auctionIds, userId)
                .forEach(bid -> result.putIfAbsent(bid.getAuction().getId(), bid));
        return result;
    }

    private Optional<Bid> currentUserBid(Integer userId, Integer auctionId) {
        if (userId == null) {
            return Optional.empty();
        }
        return bidRepository.findFirstByAuctionIdAndBidderIdOrderByCreatedAtDesc(auctionId, userId);
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
                .startsAt(toInstant(auction.getOpenTime()))
                .endsAt(toInstant(auction.getCloseTime()))
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
                .startsAt(toInstant(auction.getOpenTime()))
                .endsAt(toInstant(auction.getCloseTime()))
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
                .createdAt(toInstant(bid.getCreatedAt()))
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
