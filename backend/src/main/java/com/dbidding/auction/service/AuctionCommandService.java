package com.dbidding.auction.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.dto.AuctionCreateRequest;
import com.dbidding.auction.dto.AuctionCreateResponse;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.metrics.AuctionMetrics.BidResult;
import com.dbidding.auction.metrics.AuctionMetrics.CloseResult;
import com.dbidding.auction.metrics.AuctionMetrics.LockOperation;
import com.dbidding.auction.port.ImageUploadPort;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.card.service.CardService;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.order.OrderService;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionCommandService {
    private static final int MAX_IMAGE_COUNT = 8;
    private static final Duration BID_EXTENSION_WINDOW = Duration.ofMinutes(5);
    private static final Duration BID_EXTENSION_DURATION = Duration.ofMinutes(5);

    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final ImageUploadPort imageUploadPort;
    private final AuctionEventPublisher auctionEventPublisher;
    private final CardService cardService;
    private final OrderService orderService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionMetrics auctionMetrics;

    @Transactional
    public AuctionCreateResponse create(Integer userId, AuctionCreateRequest request, String idempotencyKey) {
        validateIdempotencyKey(idempotencyKey);
        validateCreateRequest(request);

        String requestHash = createRequestHash(request);
        Optional<AuctionCreateResponse> idempotentResponse = findIdempotentCreateResponse(
                userId,
                idempotencyKey,
                requestHash
        );
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        CardSnapshot card = cardService.getCardSnapshot(request.itemId());
        boolean psaVerified = validatePsaCertification(card, request);
        List<ImageUploadPort.ResolvedImage> images = imageUploadPort.resolveImages(request.imageUploadTokens());
        validateImages(images);

        Instant now = now();
        Instant endsAt = now.plus(Duration.ofHours(request.durationHours()));
        Auction auction = Auction.builder()
                .sellerId(userId)
                .itemId(request.itemId())
                .auctionName(request.auctionName())
                .description(request.description())
                .sellerMemo(request.sellerMemo())
                .psaCertification(request.psaCertification())
                .selfGrade(request.selfGrade())
                .psaVerified(psaVerified)
                .startPrice(request.startPrice())
                .buyNowPrice(request.buyNowPrice())
                .deliveryFee(request.shippingFee())
                .openTime(now)
                .estimatedCloseTime(endsAt)
                .closeTime(endsAt)
                .bidPriceUnit(request.bidIncrement())
                .hyped(false)
                .build();
        auction.recordCreateIdempotency(idempotencyKey, requestHash);
        Auction savedAuction = auctionRepository.save(auction);
        List<AuctionImage> auctionImages = images.stream()
                .sorted(java.util.Comparator.comparingInt(ImageUploadPort.ResolvedImage::sortOrder))
                .map(image -> new AuctionImage(savedAuction, image.imagePath()))
                .toList();
        auctionImageRepository.saveAll(auctionImages);
        auctionEventPublisher.publishOpened(new AuctionOpenedEvent(
                savedAuction.getId(),
                card.cardId(),
                card.name(),
                card.psaGrade(),
                card.language(),
                card.thumbnailUrl(),
                savedAuction.getSellerId(),
                savedAuction.getStartPrice(),
                savedAuction.getCurrentPrice(),
                savedAuction.getBidPriceUnit(),
                savedAuction.getBidCount(),
                savedAuction.getCloseTime(),
                savedAuction.getStatus(),
                now
        ));

        AuctionCreateResponse response = createResponse(savedAuction);
        publishCloseScheduleChanged(savedAuction, "auction_created");
        return response;
    }

    @Transactional
    public BidResponses.BidResult participate(
            Integer userId,
            Integer auctionId,
            BidCreateRequest request,
            String idempotencyKey
    ) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            BidResponses.BidResult result = participateInternal(userId, auctionId, request, idempotencyKey);
            auctionMetrics.finishBid(sample, BidResult.ACCEPTED);
            return result;
        } catch (ResponseStatusException exception) {
            auctionMetrics.finishBid(sample, BidResult.REJECTED);
            throw exception;
        } catch (RuntimeException exception) {
            auctionMetrics.finishBid(sample, BidResult.ERROR);
            throw exception;
        }
    }

    private BidResponses.BidResult participateInternal(
            Integer userId,
            Integer auctionId,
            BidCreateRequest request,
            String idempotencyKey
    ) {
        validateIdempotencyKey(idempotencyKey);
        String requestHash = bidRequestHash(request);
        Auction auction = findByIdForUpdate(auctionId, LockOperation.BID)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "존재하지 않는 경매입니다."));
        Optional<BidResponses.BidResult> idempotentResponse = findIdempotentBidResponse(
                userId,
                auctionId,
                idempotencyKey,
                requestHash,
                auction
        );
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        validateNotSellerBid(userId, auction);
        long bidPrice = bidPrice(auction, request.price());
        boolean buyNow = isBuyNowBid(auction, request.price());
        Bid previousLeadingBid = highestBid(auction.getId()).orElse(null);
        if (!buyNow) {
            validateNotCurrentLeadingBidder(userId, previousLeadingBid, auction.getId());
        }

        Instant bidAt = now();
        Instant previousCloseTime = auction.getCloseTime();
        boolean closeTimeExtended = placeBid(auction, bidPrice, bidAt);
        WalletBalanceResponse wallet;
        if (shouldReleasePreviousHoldFirst(previousLeadingBid, userId)) {
            outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt);
            wallet = holdBidAmount(userId, auction.getId(), bidPrice);
        } else {
            wallet = holdBidAmount(userId, auction.getId(), bidPrice);
            outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt);
        }

        Bid currentLeadingBid = bidRepository.save(Bid.leading(
                userId,
                auction,
                bidPrice,
                bidAt,
                idempotencyKey,
                requestHash
        ));
        publishBidPlaced(auction, userId, auction.getItemId(), previousLeadingBid, bidAt);
        if (buyNow) {
            closeLockedAuction(auction, bidAt);
        }
        log.info(
                "event=auction.bid.accepted auctionId={} bidderId={} bidId={} bidPrice={} currentPrice={} bidCount={} previousLeadingBidId={} closeTimeExtended={} previousCloseTime={} currentCloseTime={} status={}",
                auction.getId(), userId, currentLeadingBid.getId(), request.price(), auction.getCurrentPrice(),
                auction.getBidCount(), previousLeadingBid == null ? null : previousLeadingBid.getId(),
                closeTimeExtended, previousCloseTime, auction.getCloseTime(), auction.getStatus()
        );
        if (closeTimeExtended && !buyNow) {
            log.info(
                    "event=auction.close_time.extended auctionId={} bidId={} bidAt={} previousCloseTime={} extendedCloseTime={} extensionWindowMinutes={} extensionDurationMinutes={}",
                    auction.getId(), currentLeadingBid.getId(), bidAt, previousCloseTime, auction.getCloseTime(),
                    BID_EXTENSION_WINDOW.toMinutes(), BID_EXTENSION_DURATION.toMinutes()
            );
            publishCloseScheduleChanged(auction, "close_time_extended");
        }

        auctionRepository.flush();
        return bidResult(currentLeadingBid, auction, wallet);
    }

    private long bidPrice(Auction auction, long requestedPrice) {
        Long buyNowPrice = auction.getBuyNowPrice();
        return buyNowPrice != null && requestedPrice >= buyNowPrice ? buyNowPrice : requestedPrice;
    }

    private boolean isBuyNowBid(Auction auction, long requestedPrice) {
        Long buyNowPrice = auction.getBuyNowPrice();
        return buyNowPrice != null && requestedPrice >= buyNowPrice;
    }

    @Transactional
    public AuctionCloseResponse closeAuction(Integer auctionId) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            AuctionCloseResponse response = closeAuctionInternal(auctionId);
            CloseResult result = response.winnerId() == null
                    ? CloseResult.WITHOUT_TRADE
                    : CloseResult.WITH_WINNER;
            auctionMetrics.finishClose(sample, result);
            return response;
        } catch (RuntimeException exception) {
            auctionMetrics.finishClose(sample, CloseResult.ERROR);
            throw exception;
        }
    }

    private AuctionCloseResponse closeAuctionInternal(Integer auctionId) {
        Instant now = now();
        Auction auction = findByIdForUpdate(auctionId, LockOperation.CLOSE)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "경매를 찾을 수 없습니다."));
        if (auction.getStatus() == AuctionStatus.ENDED || auction.getStatus() == AuctionStatus.FAILED) {
            return closeResponse(auction, closedWinningBid(auction.getId()).orElse(null));
        }
        validateCloseDue(auction, now);
        return closeLockedAuction(auction, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<AuctionCloseResponse> closeDueAuction(Integer auctionId, Instant now) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            Optional<Auction> auction = findByIdForUpdate(auctionId, LockOperation.CLOSE);
            if (auction.isEmpty() || !isDueCloseTarget(auction.get(), now)) {
                return Optional.empty();
            }
            AuctionCloseResponse response = closeLockedAuction(auction.get(), now);
            auctionMetrics.finishClose(sample, response.winnerId() == null
                    ? CloseResult.WITHOUT_TRADE
                    : CloseResult.WITH_WINNER);
            return Optional.of(response);
        } catch (RuntimeException exception) {
            auctionMetrics.finishClose(sample, CloseResult.ERROR);
            throw exception;
        }
    }

    private void validateIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key 헤더가 필요합니다.");
        }
        if (idempotencyKey.length() > 64) {
            throw new ResponseStatusException(BAD_REQUEST, "Idempotency-Key는 64자 이하여야 합니다.");
        }
    }

    private Optional<Auction> findByIdForUpdate(Integer auctionId, LockOperation operation) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            return auctionRepository.findByIdForUpdate(auctionId);
        } finally {
            auctionMetrics.finishAuctionLockWait(sample, operation);
        }
    }

    private void validateNotSellerBid(Integer userId, Auction auction) {
        if (auction.getSellerId().equals(userId)) {
            log.warn("event=auction.bid.rejected_self_bid auctionId={} sellerId={} bidderId={}",
                    auction.getId(), auction.getSellerId(), userId);
            throw new ResponseStatusException(FORBIDDEN, "판매자는 자신의 경매에 입찰할 수 없습니다.");
        }
    }

    private void validateNotCurrentLeadingBidder(Integer userId, Bid previousLeadingBid, Integer auctionId) {
        if (previousLeadingBid != null && previousLeadingBid.getBidderId().equals(userId)) {
            log.warn("event=auction.bid.rejected_leading_bidder auctionId={} bidderId={}", auctionId, userId);
            throw new ResponseStatusException(CONFLICT, "현재 최고 입찰자는 추가 입찰할 수 없습니다.");
        }
    }

    private void validateCreateRequest(AuctionCreateRequest request) {
        if (request.buyNowPrice() != null
                && request.buyNowPrice() - request.startPrice() < request.bidIncrement()) {
            throw new ResponseStatusException(BAD_REQUEST, "즉시구매가는 시작가와 호가 단위의 합 이상이어야 합니다.");
        }
        if (request.imageUploadTokens().size() > MAX_IMAGE_COUNT) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지는 최대 8장까지 등록할 수 있습니다.");
        }
    }

    private boolean validatePsaCertification(CardSnapshot card, AuctionCreateRequest request) {
        if (!"psa".equalsIgnoreCase(request.gradeType())) {
            return false;
        }
        if (request.psaCertification() == null || !request.psaCertification().matches("\\d{7,10}")) {
            throw new ResponseStatusException(BAD_REQUEST, "PSA 등급 카드는 7~10자리 PSA 인증번호가 필요합니다.");
        }
        if (!normalizePsaGrade(card.psaGrade()).equals(normalizePsaGrade(request.psaGrade()))) {
            throw new ResponseStatusException(BAD_REQUEST, "PSA 인증 등급과 선택한 카드 등급이 일치하지 않습니다.");
        }
        return true;
    }

    private String normalizePsaGrade(String grade) {
        return grade == null ? "" : grade.trim().toUpperCase().replaceFirst("^PSA\\s*", "");
    }

    private Optional<AuctionCreateResponse> findIdempotentCreateResponse(
            Integer sellerId,
            String idempotencyKey,
            String requestHash
    ) {
        Optional<Auction> existingAuction = auctionRepository.findBySellerIdAndCreateIdempotencyKey(
                sellerId,
                idempotencyKey
        );
        if (existingAuction.isEmpty()) {
            return Optional.empty();
        }
        Auction auction = existingAuction.get();
        if (!Objects.equals(auction.getCreateIdempotencyRequestHash(), requestHash)) {
            throw new ResponseStatusException(CONFLICT, "같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
        }
        return Optional.of(createResponse(auction));
    }

    private Optional<BidResponses.BidResult> findIdempotentBidResponse(
            Integer bidderId,
            Integer auctionId,
            String idempotencyKey,
            String requestHash,
            Auction auction
    ) {
        Optional<Bid> existingBid = bidRepository.findFirstByBidderIdAndAuctionIdAndIdempotencyKey(
                bidderId,
                auctionId,
                idempotencyKey
        );
        if (existingBid.isEmpty()) {
            return Optional.empty();
        }
        Bid bid = existingBid.get();
        if (!Objects.equals(bid.getIdempotencyRequestHash(), requestHash)) {
            throw new ResponseStatusException(CONFLICT, "같은 Idempotency-Key로 다른 요청을 보낼 수 없습니다.");
        }
        return Optional.of(bidResult(bid, auction, walletService.getBalance(bidderId)));
    }

    private void validateImages(List<ImageUploadPort.ResolvedImage> images) {
        if (images.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지는 1장 이상 필요합니다.");
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            throw new ResponseStatusException(BAD_REQUEST, "이미지는 최대 8장까지 등록할 수 있습니다.");
        }
    }

    private boolean placeBid(Auction auction, Long price, Instant bidAt) {
        try {
            return auction.placeBid(price, bidAt, BID_EXTENSION_WINDOW, BID_EXTENSION_DURATION);
        } catch (IllegalArgumentException exception) {
            log.warn(
                    "event=auction.bid.rejected auctionId={} bidPrice={} currentPrice={} minimumBid={} status={} closeTime={} bidAt={} reason=\"{}\"",
                    auction.getId(), price, auction.getCurrentPrice(), auction.minimumBid(), auction.getStatus(),
                    auction.getCloseTime(), bidAt, exception.getMessage()
            );
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private WalletBalanceResponse holdBidAmount(Integer bidderId, Integer auctionId, Long price) {
        try {
            return walletService.hold(bidderId, auctionId, price);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private void outbidPreviousLeadingBid(
            Bid previousLeadingBid,
            Integer currentBidderId,
            Auction auction,
            Instant occurredAt
    ) {
        if (previousLeadingBid == null) {
            return;
        }
        previousLeadingBid.markOutbid();
        if (requiresPreviousHoldRelease(previousLeadingBid, currentBidderId)) {
            walletService.release(previousLeadingBid.getBidderId(), auction.getId());
            log.info(
                    "event=auction.bid.previous_hold.released auctionId={} previousBidId={} previousBidderId={} previousBidPrice={} currentBidderId={}",
                    auction.getId(), previousLeadingBid.getId(), previousLeadingBid.getBidderId(),
                    previousLeadingBid.getBidPrice(), currentBidderId
            );
        } else {
            log.debug("event=auction.bid.previous_hold.kept auctionId={} previousBidId={} bidderId={}",
                    auction.getId(), previousLeadingBid.getId(), currentBidderId);
        }
    }

    private boolean requiresPreviousHoldRelease(Bid previousLeadingBid, Integer currentBidderId) {
        return previousLeadingBid != null
                && !previousLeadingBid.getBidderId().equals(currentBidderId);
    }

    private boolean shouldReleasePreviousHoldFirst(Bid previousLeadingBid, Integer currentBidderId) {
        return requiresPreviousHoldRelease(previousLeadingBid, currentBidderId)
                && previousLeadingBid.getBidderId() < currentBidderId;
    }

    private void validateCloseDue(Auction auction, Instant now) {
        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.ENDING) {
            throw new ResponseStatusException(BAD_REQUEST, "진행 중인 경매만 종료할 수 있습니다.");
        }
        if (auction.getCloseTime().isAfter(now)) {
            throw new ResponseStatusException(BAD_REQUEST, "아직 종료 시각이 지나지 않은 경매입니다.");
        }
    }

    private boolean isDueCloseTarget(Auction auction, Instant now) {
        return (auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.ENDING)
                && !auction.getCloseTime().isAfter(now);
    }

    private AuctionCloseResponse closeLockedAuction(Auction auction, Instant closedAt) {
        Optional<Bid> winningBid = highestBid(auction.getId());
        if (winningBid.isEmpty()) {
            auction.closeWithoutTrade(closedAt);
            publishAuctionClosed(auction, null, closedAt, cardService.getCardSnapshot(auction.getItemId()));
            log.info("event=auction.closed.without_trade auctionId={} itemId={} sellerId={} closedAt={} status={} bidCount={}",
                    auction.getId(), auction.getItemId(), auction.getSellerId(), closedAt,
                    auction.getStatus(), auction.getBidCount());
            return closeResponse(auction, null);
        }

        Bid winner = winningBid.get();
        winner.markWon();
        auction.closeWithWinningBid(winner, closedAt);
        walletService.capture(winner.getBidderId(), auction.getId(), winner.getBidPrice());
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        orderService.createFromAuctionClosed(
                auction.getId(), winner.getBidderId(), auction.getSellerId(), card.name(), winner.getBidPrice()
        );
        publishAuctionClosed(auction, winner, closedAt, card);
        log.info(
                "event=auction.closed.with_winner auctionId={} itemId={} sellerId={} winnerId={} winningBidId={} winningPrice={} closedAt={} status={} bidCount={}",
                auction.getId(), auction.getItemId(), auction.getSellerId(), winner.getBidderId(), winner.getId(),
                winner.getBidPrice(), closedAt, auction.getStatus(), auction.getBidCount()
        );
        return closeResponse(auction, winner);
    }

    private void publishBidPlaced(
            Auction auction,
            Integer bidderId,
            Integer itemId,
            Bid previousLeadingBid,
            Instant occurredAt
    ) {
        auctionEventPublisher.publishBidPlaced(new BidPlacedEvent(
                auction.getId(),
                itemId,
                bidderId,
                previousLeadingBid == null ? null : previousLeadingBid.getBidderId(),
                previousLeadingBid == null ? null : previousLeadingBid.getId(),
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                auction.getBidPriceUnit(),
                auction.getBidCount(),
                auction.getCloseTime(),
                auction.getStatus(),
                occurredAt
        ));
    }

    private void publishAuctionClosed(Auction auction, Bid winningBid, Instant occurredAt, CardSnapshot card) {
        Integer winnerId = winningBid == null ? null : winningBid.getBidderId();
        Long winningPrice = winningBid == null ? null : winningBid.getBidPrice();

        auctionEventPublisher.publishClosed(new AuctionClosedEvent(
                auction.getId(),
                card.cardId(),
                card.name(),
                card.psaGrade(),
                card.language(),
                card.thumbnailUrl(),
                winnerId,
                auction.getSellerId(),
                auction.getStartPrice(),
                auction.getCurrentPrice(),
                winningPrice,
                auction.getBidPriceUnit(),
                auction.getBidCount(),
                auction.getCloseTime(),
                auction.getStatus(),
                occurredAt
        ));
    }

    private Optional<Bid> highestBid(Integer auctionId) {
        return bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auctionId, BidStatus.LEADING);
    }

    private Optional<Bid> closedWinningBid(Integer auctionId) {
        return bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auctionId, BidStatus.WON);
    }

    private AuctionCloseResponse closeResponse(Auction auction, Bid winningBid) {
        return new AuctionCloseResponse(
                auction.getId(),
                auction.getStatus(),
                winningBid == null ? null : winningBid.getBidderId(),
                winningBid == null ? null : winningBid.getId(),
                winningBid == null ? null : winningBid.getBidPrice(),
                auction.getCloseTime()
        );
    }

    private AuctionCreateResponse createResponse(Auction auction) {
        return AuctionCreateResponse.builder()
                .id(auction.getId())
                .status(auction.getStatus())
                .startsAt(auction.getOpenTime())
                .endsAt(auction.getCloseTime())
                .build();
    }

    private BidResponses.BidResult bidResult(
            Bid bid,
            Auction auction,
            WalletBalanceResponse wallet
    ) {
        return new BidResponses.BidResult(
                new BidResponses.BidDetail(
                        bid.getId(),
                        bid.getBidPrice(),
                        bid.getStatus(),
                        bid.getCreatedAt()
                ),
                new BidResponses.AuctionSnapshot(
                        auction.getId(),
                        auction.getCurrentPrice(),
                        auction.minimumBid(),
                        auction.getBidCount(),
                        auction.getCloseTime()
                ),
                new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance())
        );
    }

    private Instant now() {
        return clock.instant();
    }

    private void publishCloseScheduleChanged(Auction auction, String reason) {
        eventPublisher.publishEvent(new AuctionCloseScheduleChangedEvent(
                auction.getId(),
                auction.getCloseTime(),
                reason
        ));
    }

    private String createRequestHash(AuctionCreateRequest request) {
        return sha256(
                request.itemId(),
                request.auctionName(),
                request.description(),
                request.sellerMemo(),
                request.psaCertification(),
                request.gradeType(),
                request.selfGrade(),
                request.imageUploadTokens(),
                request.startPrice(),
                request.bidIncrement(),
                request.buyNowPrice(),
                request.durationHours(),
                request.shippingFee()
        );
    }

    private String bidRequestHash(BidCreateRequest request) {
        return sha256(request.price());
    }

    private String sha256(Object... values) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object value : values) {
                appendDigestValue(digest, value);
                digest.update((byte) 0);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private void appendDigestValue(MessageDigest digest, Object value) {
        if (value instanceof List<?> list) {
            digest.update("[list]".getBytes(StandardCharsets.UTF_8));
            for (Object item : list) {
                appendDigestValue(digest, item);
                digest.update((byte) 1);
            }
            return;
        }
        digest.update((value == null ? "" : String.valueOf(value)).getBytes(StandardCharsets.UTF_8));
    }
}
