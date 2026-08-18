package com.dbidding.auction.service;

import com.dbidding.auction.IdempotencyKeys;
import com.dbidding.auction.bid.dto.AuctionCloseData;
import com.dbidding.auction.bid.dto.BidCommand;
import com.dbidding.auction.bid.dto.BidEventData;
import com.dbidding.auction.bid.dto.BidExecutionResult;
import com.dbidding.auction.bid.BidExecutor;
import com.dbidding.auction.bid.dto.RedisAuctionCreateCommand;
import com.dbidding.auction.bid.redis.RedisAuctionCreateExecutor;
import com.dbidding.auction.bid.dto.RedisAuctionCreateResult;
import com.dbidding.auction.bid.redis.RedisCardStateReader;
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
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.metrics.AuctionMetrics.BidResult;
import com.dbidding.auction.metrics.AuctionMetrics.CloseResult;
import com.dbidding.auction.metrics.AuctionMetrics.LockOperation;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import com.dbidding.card.service.CardService;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.order.service.OrderService;
import com.dbidding.upload.adapter.AuctionImageUploadAdapter;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuctionCommandService {
    private static final int MAX_IMAGE_COUNT = 8;

    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final AuctionImageUploadAdapter imageUploadAdapter;
    private final AuctionEventPublisher auctionEventPublisher;
    private final AuctionStreamPublisher auctionStreamPublisher;
    private final CardService cardService;
    private final OrderService orderService;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionMetrics auctionMetrics;
    private final BidExecutor bidExecutor;
    @Autowired(required = false)
    private RedisAuctionCreateExecutor redisAuctionCreateExecutor;
    @Autowired(required = false)
    private RedisCardStateReader redisCardStateReader;

    @Transactional
    public AuctionCreateResponse create(Integer userId, AuctionCreateRequest request, String idempotencyKey) {
        IdempotencyKeys.validate(idempotencyKey);
        validateCreateRequest(request);

        CardSnapshot card = cardSnapshotForCreate(request.itemId());
        boolean psaVerified = validatePsaCertification(card, request);
        List<AuctionImageUploadAdapter.ResolvedImage> images = imageUploadAdapter.resolveImages(request.imageUploadTokens());
        validateImages(images);

        Instant now = now();
        Instant endsAt = now.plus(Duration.ofHours(request.durationHours()));
        String requestHash = createRequestHash(request);
        if (redisAuctionCreateExecutor != null) {
            return createInRedis(userId, request, idempotencyKey, requestHash, card, psaVerified, images, now, endsAt);
        }
        Optional<AuctionCreateResponse> idempotentResponse = findIdempotentCreateResponse(
                userId,
                idempotencyKey,
                requestHash
        );
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

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
                .sorted(java.util.Comparator.comparingInt(AuctionImageUploadAdapter.ResolvedImage::sortOrder))
                .map(image -> new AuctionImage(savedAuction, image.imagePath()))
                .toList();
        auctionImageRepository.saveAll(auctionImages);
        AuctionOpenedEvent openedEvent = new AuctionOpenedEvent(
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
                savedAuction.getEstimatedCloseTime(),
                savedAuction.getStatus(),
                now
        );
        auctionEventPublisher.publishOpened(openedEvent);
        auctionStreamPublisher.publish(AuctionStreamPayload.created(openedEvent));

        AuctionCreateResponse response = createResponse(savedAuction);
        publishCloseScheduleChanged(savedAuction, "auction_created");
        return response;
    }

    private AuctionCreateResponse createInRedis(
            Integer userId,
            AuctionCreateRequest request,
            String idempotencyKey,
            String requestHash,
            CardSnapshot card,
            boolean psaVerified,
            List<AuctionImageUploadAdapter.ResolvedImage> images,
            Instant now,
            Instant endsAt
    ) {
        RedisAuctionCreateResult created = redisAuctionCreateExecutor.execute(new RedisAuctionCreateCommand(
                userId, request.itemId(), card.name(), card.setName(), card.psaGrade(), card.language(), card.thumbnailUrl(),
                request.auctionName(), request.description(), request.sellerMemo(),
                request.psaCertification(), request.selfGrade(), psaVerified, request.startPrice(), request.buyNowPrice(),
                request.shippingFee(), request.bidIncrement(), images.stream()
                        .sorted(java.util.Comparator.comparingInt(AuctionImageUploadAdapter.ResolvedImage::sortOrder))
                        .map(AuctionImageUploadAdapter.ResolvedImage::imagePath).toList(),
                endsAt, idempotencyKey, requestHash
        ));
        AuctionOpenedEvent openedEvent = new AuctionOpenedEvent(
                created.auctionId(), card.cardId(), card.name(), card.psaGrade(), card.language(), card.thumbnailUrl(),
                userId, request.startPrice(), request.startPrice(), request.bidIncrement(), 0,
                created.closeTime(), created.status(), now
        );
        if (!created.replayed()) {
            auctionEventPublisher.publishOpened(openedEvent);
            auctionStreamPublisher.publish(AuctionStreamPayload.created(openedEvent));
            eventPublisher.publishEvent(new AuctionCloseScheduleChangedEvent(
                    created.auctionId(), created.closeTime(), "auction_created"
            ));
        }
        return AuctionCreateResponse.builder()
                .id(created.auctionId())
                .status(created.status())
                .startsAt(created.occurredAt())
                .endsAt(created.closeTime())
                .build();
    }

    private CardSnapshot cardSnapshotForCreate(Integer itemId) {
        return redisCardStateReader == null
                ? cardService.getCardSnapshot(itemId)
                : redisCardStateReader.getCardSnapshot(itemId);
    }

    public BidResponses.BidResult participate(
            Integer userId,
            Integer auctionId,
            BidCreateRequest request,
            String idempotencyKey
    ) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            BidExecutionResult outcome = bidExecutor.execute(
                    new BidCommand(userId, auctionId, request.price(), idempotencyKey)
            );
            publishBidEvents(userId, auctionId, outcome);
            auctionMetrics.finishBid(sample, BidResult.ACCEPTED);
            return outcome.result();
        } catch (AuctionException exception) {
            auctionMetrics.finishBid(sample, BidResult.REJECTED);
            throw exception;
        } catch (RuntimeException exception) {
            auctionMetrics.finishBid(sample, BidResult.ERROR);
            throw exception;
        }
    }

    /**
     * {@code bidExecutor.execute()}는 이벤트를 발행하지 않으므로(#281), 여기서 result로부터
     * 조립해서 발행한다. buyNow로 즉시낙찰된 경우 {@code AuctionClosedEvent}도 같은 자리에서
     * 순서대로 발행한다.
     */
    private void publishBidEvents(Integer userId, Integer auctionId, BidExecutionResult outcome) {
        BidEventData data = outcome.eventData();
        if (data == null) {
            return;
        }
        BidResponses.AuctionSnapshot auction = outcome.result().auction();
        Instant occurredAt = outcome.result().bid().createdAt();

        BidPlacedEvent bidPlaced = new BidPlacedEvent(
                auctionId, data.itemId(), userId, data.previousBidderId(), data.previousBidId(),
                data.startPrice(), auction.currentPrice(), data.bidIncrement(), auction.bidCount(),
                auction.endsAt(), data.status(), occurredAt
        );
        auctionEventPublisher.publishBidPlaced(bidPlaced);
        auctionStreamPublisher.publish(AuctionStreamPayload.bidPlaced(bidPlaced));

        if (data.closeData() != null) {
            AuctionCloseData close = data.closeData();
            AuctionClosedEvent closed = new AuctionClosedEvent(
                    auctionId, close.cardId(), close.cardName(), close.cardPsaGrade(), close.cardLanguage(),
                    close.cardThumbnailUrl(), userId, close.sellerId(), data.startPrice(), auction.currentPrice(),
                    auction.currentPrice(), data.bidIncrement(), auction.bidCount(), auction.endsAt(), data.status(),
                    occurredAt
            );
            auctionEventPublisher.publishClosed(closed);
            auctionStreamPublisher.publish(AuctionStreamPayload.closed(closed));
        }
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
                .orElseThrow(AuctionException::notFound);
        if (auction.getStatus() == AuctionStatus.ENDED || auction.getStatus() == AuctionStatus.FAILED) {
            return closeResponse(auction, closedWinningBid(auction.getId()).orElse(null));
        }
        validateCloseDue(auction, now);
        return closeLockedAuction(auction, now);
    }

    /**
     * READ_COMMITTED로 고정한다 — DbBidExecutor와 동일한 격리수준을 맞춰야
     * WalletService.hold/release/capture()가 지갑 행 락만으로 최신 홀드
     * 합계를 안전하게 읽을 수 있다(#393). 기본값(REPEATABLE READ)에서는
     * 지갑 행 락을 획득해도 트랜잭션 시작 시점 스냅샷을 볼 수 있어, 동시에
     * 커밋된 다른 홀드 변경을 놓칠 수 있다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
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

    private Optional<Auction> findByIdForUpdate(Integer auctionId, LockOperation operation) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            return auctionRepository.findByIdForUpdate(auctionId);
        } finally {
            auctionMetrics.finishAuctionLockWait(sample, operation);
        }
    }

    private void validateCreateRequest(AuctionCreateRequest request) {
        if (request.buyNowPrice() != null
                && request.buyNowPrice() - request.startPrice() < request.bidIncrement()) {
            throw AuctionException.invalidRequest("즉시구매가는 시작가와 호가 단위의 합 이상이어야 합니다.");
        }
        if (request.imageUploadTokens().size() > MAX_IMAGE_COUNT) {
            throw AuctionException.invalidRequest("이미지는 최대 8장까지 등록할 수 있습니다.");
        }
    }

    private boolean validatePsaCertification(CardSnapshot card, AuctionCreateRequest request) {
        if (!"psa".equalsIgnoreCase(request.gradeType())) {
            return false;
        }
        if (request.psaCertification() == null || !request.psaCertification().matches("\\d{7,10}")) {
            throw AuctionException.invalidRequest("PSA 등급 카드는 7~10자리 PSA 인증번호가 필요합니다.");
        }
        if (!normalizePsaGrade(card.psaGrade()).equals(normalizePsaGrade(request.psaGrade()))) {
            throw AuctionException.invalidRequest("PSA 인증 등급과 선택한 카드 등급이 일치하지 않습니다.");
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
            throw AuctionException.idempotencyConflict();
        }
        return Optional.of(createResponse(auction));
    }

    private void validateImages(List<AuctionImageUploadAdapter.ResolvedImage> images) {
        if (images.isEmpty()) {
            throw AuctionException.invalidRequest("이미지는 1장 이상 필요합니다.");
        }
        if (images.size() > MAX_IMAGE_COUNT) {
            throw AuctionException.invalidRequest("이미지는 최대 8장까지 등록할 수 있습니다.");
        }
    }

    private void validateCloseDue(Auction auction, Instant now) {
        if (auction.getStatus() != AuctionStatus.OPEN && auction.getStatus() != AuctionStatus.ENDING) {
            throw AuctionException.invalidRequest("진행 중인 경매만 종료할 수 있습니다.");
        }
        if (auction.getCloseTime().isAfter(now)) {
            throw AuctionException.invalidRequest("아직 종료 시각이 지나지 않은 경매입니다.");
        }
    }

    private boolean isDueCloseTarget(Auction auction, Instant now) {
        return (auction.getStatus() == AuctionStatus.OPEN || auction.getStatus() == AuctionStatus.ENDING)
                && !auction.getCloseTime().isAfter(now);
    }

    private AuctionCloseResponse closeLockedAuction(Auction auction, Instant closedAt) {
        Optional<Bid> winningBid = highestBid(auction.getId());
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        if (winningBid.isEmpty()) {
            auction.closeWithoutTrade(closedAt);
            publishAuctionClosed(auction, null, closedAt, card);
            log.info("event=auction.closed.without_trade auctionId={} itemId={} sellerId={} closedAt={} status={} bidCount={}",
                    auction.getId(), auction.getItemId(), auction.getSellerId(), closedAt,
                    auction.getStatus(), auction.getBidCount());
            return closeResponse(auction, null);
        }

        Bid winner = winningBid.get();
        winner.markWon();
        auction.closeWithWinningBid(winner, closedAt);
        walletService.capture(winner.getBidderId(), auction.getId(), winner.getBidPrice());
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

    private void publishAuctionClosed(Auction auction, Bid winningBid, Instant occurredAt, CardSnapshot card) {
        Integer winnerId = winningBid == null ? null : winningBid.getBidderId();
        Long winningPrice = winningBid == null ? null : winningBid.getBidPrice();

        AuctionClosedEvent event = new AuctionClosedEvent(
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
        );
        auctionEventPublisher.publishClosed(event);
        auctionStreamPublisher.publish(AuctionStreamPayload.closed(event));
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
                .endsAt(auction.getEstimatedCloseTime())
                .build();
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
        return IdempotencyKeys.sha256(
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
}
