package com.dbidding.auction.stream;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionImage;
import com.dbidding.auction.domain.AuctionTimelineEvent;
import com.dbidding.auction.domain.AuctionBidEventProjectionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.repository.AuctionTimelineEventRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.AuctionImageRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.account.repository.AccountRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.order.service.OrderService;
import com.dbidding.order.repository.OrderRepository;
import com.dbidding.order.service.redis.RedisOrderRealtimeStateProjection;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
@RequiredArgsConstructor
public class AuctionBidStreamPersistenceService {
    private final AuctionTimelineEventRepository inboxRepository;
    private final AuctionRepository auctionRepository;
    private final AuctionImageRepository auctionImageRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final AccountRepository accountRepository;
    private final com.dbidding.wallet.service.WalletProjectionService walletProjectionService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;
    private final Optional<RedisOrderRealtimeStateProjection> orderRealtimeStateProjection;
    private final CardService cardService;
    /** 생성자 호환을 유지하되, 실시간 발행은 Redis 승인 경로에서만 수행한다. */
    @SuppressWarnings("unused")
    private final AuctionEventPublisher auctionEventPublisher;
    private final Clock clock;
    @PersistenceContext
    private EntityManager entityManager;

    /** Stream 수신 자체는 projection 실패와 독립적으로 반드시 보존한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuctionTimelineEvent recordPending(AuctionWalletTimelineEvent event) {
        return recordPending(event, event.archivePayload());
    }

    /** Projection worker가 DB만 읽어 재구성할 수 있도록 원본 Stream payload를 함께 보관한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuctionTimelineEvent recordPending(AuctionWalletTimelineEvent event, String rawPayload) {
        return inboxRepository.findByStreamId(event.streamId())
                .orElseGet(() -> inboxRepository.save(archive(event, event instanceof BidAcceptedStreamEvent bid ? bid.auctionId() : event instanceof AuctionCloseRequestedStreamEvent close ? close.auctionId() : event instanceof AuctionEndingStartedStreamEvent ending ? ending.auctionId() : event instanceof AuctionCreatedStreamEvent created ? created.auctionId() : event instanceof OrderStateChangedStreamEvent order ? order.auctionId() : null,
                event instanceof BidAcceptedStreamEvent bid ? bid.auctionVersion() : event instanceof OrderStateChangedStreamEvent order ? order.orderVersion() : null, rawPayload)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuctionTimelineEvent recordMalformed(String streamId, Map<String, String> payload) {
        return inboxRepository.findByStreamId(streamId).orElseGet(() -> inboxRepository.save(new AuctionTimelineEvent(
                streamId, null, null, null, payload.getOrDefault("eventType", "unknown"), malformedSchemaVersion(payload),
                payload.toString(), Instant.now(), clock.instant()
        )));
    }

    private int malformedSchemaVersion(Map<String, String> payload) {
        try {
            return Integer.parseInt(payload.getOrDefault("schemaVersion", "0"));
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public boolean hasProjectionError() {
        return inboxRepository.existsByProjectionStatus(AuctionBidEventProjectionStatus.ERROR);
    }

    /** ERROR로 막힌 aggregate(경매)를 제외하고 가장 오래된 PENDING을 고른다. 한 aggregate의
     * ERROR가 관계없는 다른 aggregate의 처리까지 막는 head-of-line blocking을 없앤다. */
    @Transactional(readOnly = true)
    public Optional<AuctionTimelineEvent> findNextEligiblePending() {
        List<Integer> blocked = inboxRepository.findAuctionIdsWithError();
        if (blocked.isEmpty()) {
            return inboxRepository.findFirstByProjectionStatusOrderByIdAsc(AuctionBidEventProjectionStatus.PENDING);
        }
        return inboxRepository.findEligiblePending(blocked, org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst();
    }

    /** 첫 오류를 다시 PENDING으로 전환한다. 이후 투영 worker는 DB inbox의 ID 순서대로 처리한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuctionTimelineEvent requeueFirstError() {
        return inboxRepository.findFirstByProjectionStatusOrderByIdAsc(AuctionBidEventProjectionStatus.ERROR)
                .map(inbox -> {
                    inbox.requeueForProjection();
                    return inbox;
                })
                .orElse(null);
    }

    /** 기존 호출부 및 단위 테스트 호환용 동기 projection 경로. */
    public void persist(AuctionWalletTimelineEvent event) {
        recordPending(event);
        project(event);
        markProcessed(event.streamId());
    }

    /** PENDING으로 기록된 이벤트를 실제 도메인 테이블에 반영한다. */
    @Transactional
    public void project(AuctionWalletTimelineEvent event) {
        if (event instanceof WalletStateChangedStreamEvent walletChanged) {
            walletProjectionService.project(walletChanged);
            return;
        }
        if (event instanceof OrderStateChangedStreamEvent orderChanged) {
            projectOrderState(orderChanged);
            return;
        }
        if (event instanceof AuctionCreatedStreamEvent created) {
            cardService.getCardSnapshot(created.itemId());
            if (!accountRepository.existsById(created.sellerId())) throw new InvalidBidStreamEventException("존재하지 않는 판매자입니다: " + created.sellerId());
            if (auctionRepository.existsById(created.auctionId())) return;
            if (auctionRepository.findBySellerIdAndCreateIdempotencyKey(created.sellerId(), created.idempotencyKey()).isPresent()) return;
            insertCreatedAuction(created);
            Auction projectedAuction = entityManager.getReference(Auction.class, created.auctionId());
            auctionImageRepository.saveAll(created.imagePaths().stream().map(path -> new AuctionImage(projectedAuction, path)).toList());
            return;
        }
        if (event instanceof AuctionCloseRequestedStreamEvent close) {
            closeAuction(close);
            return;
        }
        if (event instanceof AuctionEndingStartedStreamEvent ending) {
            projectEndingTransition(ending);
            return;
        }
        persistBid((BidAcceptedStreamEvent) event);
    }

    private void projectEndingTransition(AuctionEndingStartedStreamEvent event) {
        Auction auction = auctionRepository.findByIdForUpdate(event.auctionId())
                .orElseThrow(() -> new InvalidBidStreamEventException("존재하지 않는 ENDING 대상 경매입니다: " + event.auctionId()));
        try {
            auction.applyEndingTransition(event.closeTime());
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException(exception.getMessage(), exception);
        }
    }

    private void insertCreatedAuction(AuctionCreatedStreamEvent event) {
        entityManager.createNativeQuery("""
                INSERT INTO auctions (
                    id, user_id, item_id, auction_name, description, seller_memo, psa_certification, self_grade,
                    psa_verified, start_price, current_price, buy_now_price, delivery_fee, status, open_time,
                    estimated_close_time, close_time, bid_count, bid_price_unit, last_bid_event_version, is_hyped,
                    idempotency_key, idempotency_request_hash
                ) VALUES (
                    :id, :sellerId, :itemId, :auctionName, :description, :sellerMemo, :psaCertification, :selfGrade,
                    :psaVerified, :startPrice, :currentPrice, :buyNowPrice, :deliveryFee, 'OPEN', :openTime,
                    :closeTime, :closeTime, 0, :bidPriceUnit, 0, FALSE, :idempotencyKey, :idempotencyRequestHash
                )
                """)
                .setParameter("id", event.auctionId()).setParameter("sellerId", event.sellerId())
                .setParameter("itemId", event.itemId()).setParameter("auctionName", event.auctionName())
                .setParameter("description", event.description()).setParameter("sellerMemo", event.sellerMemo())
                .setParameter("psaCertification", event.psaCertification()).setParameter("selfGrade", event.selfGrade())
                .setParameter("psaVerified", event.psaVerified()).setParameter("startPrice", event.startPrice())
                .setParameter("currentPrice", event.startPrice()).setParameter("buyNowPrice", event.buyNowPrice())
                .setParameter("deliveryFee", event.deliveryFee()).setParameter("openTime", event.occurredAt())
                .setParameter("closeTime", event.closeTime()).setParameter("bidPriceUnit", event.bidPriceUnit())
                .setParameter("idempotencyKey", event.idempotencyKey())
                .setParameter("idempotencyRequestHash", event.idempotencyRequestHash())
                .executeUpdate();
    }

    private void projectOrderState(OrderStateChangedStreamEvent event) {
        com.dbidding.order.domain.Order order = orderRepository.findByIdForUpdate(event.orderId())
                .orElseThrow(() -> new InvalidBidStreamEventException("존재하지 않는 주문 상태 이벤트입니다: " + event.orderId()));
        if (!order.getAuctionId().equals(event.auctionId()) || !order.getBuyerId().equals(event.buyerId())
                || !order.getSellerId().equals(event.sellerId())) {
            throw new InvalidBidStreamEventException("주문 상태 이벤트의 참여자 정보가 일치하지 않습니다.");
        }
        order.applyProjectedStatus(event.status());
        walletProjectionService.project(new WalletStateChangedStreamEvent(
                event.streamId(), event.eventId(), "wallet." + event.eventType().substring("order.".length()), event.walletUserId(),
                event.walletVersion(), event.availableBalance(), event.frozenBalance(), event.auctionId(), null, null,
                event.transactionType(), event.transactionAmount(), event.idempotencyKey(), event.occurredAt()
        ));
        orderRealtimeStateProjection.ifPresent(projection -> projection.markProjectedStatusAfterCommit(
                event.auctionId(), event.orderId(), event.status().name()));
        // 주문 알림과 wallet SSE는 Redis 승인 직후 발행한다. projection은 DB 반영만 담당한다.
    }

    private void closeAuction(AuctionCloseRequestedStreamEvent event) {
        Auction auction = auctionRepository.findByIdForUpdate(event.auctionId())
                .orElseThrow(() -> new InvalidBidStreamEventException("존재하지 않는 종료 대상 경매입니다: " + event.auctionId()));
        if ((auction.getStatus() != com.dbidding.auction.domain.AuctionStatus.OPEN && auction.getStatus() != com.dbidding.auction.domain.AuctionStatus.ENDING)
                || auction.getCloseTime().truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
                .isAfter(event.occurredAt().truncatedTo(java.time.temporal.ChronoUnit.MILLIS))) {
            throw new InvalidBidStreamEventException("아직 종료할 수 없는 경매입니다: " + event.auctionId());
        }
        java.util.Optional<Bid> winning = bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auction.getId(), BidStatus.LEADING);
        if (winning.isEmpty()) { auction.closeWithoutTrade(event.occurredAt()); return; }
        Bid winner = winning.get();
        winner.markWon();
        auction.closeWithWinningBid(winner, event.occurredAt());
        walletService.capture(winner.getBidderId(), auction.getId(), winner.getBidPrice());
        completeBuyNow(auction, winner, event.occurredAt(), event.streamId());
    }

    private void persistBid(BidAcceptedStreamEvent event) {
        Auction auction = auctionRepository.findByIdForUpdate(event.auctionId())
                .orElseThrow(() -> new InvalidBidStreamEventException("존재하지 않는 경매의 입찰 이벤트입니다: " + event.auctionId()));
        Bid currentLeadingBid = bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(
                auction.getId(), BidStatus.LEADING
        ).orElse(null);
        Bid bid = apply(event, auction, currentLeadingBid);
        if (bid != null) {
            bidRepository.save(bid);
            if (event.isBuyNow()) {
                completeBuyNow(auction, bid, event.occurredAt(), event.streamId());
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(String streamId) {
        inboxRepository.findByStreamId(streamId).ifPresent(inbox -> inbox.markProcessed(clock.instant()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markError(String streamId, RuntimeException exception) {
        AuctionTimelineEvent inbox = inboxRepository.findByStreamId(streamId)
                .orElseThrow(() -> new IllegalStateException("수신 기록이 없는 Stream 이벤트입니다: " + streamId));
        boolean firstError = !hasProjectionError();
        inbox.markError(exception.getClass().getSimpleName() + ": " + exception.getMessage());
        if (inbox.getAuctionId() != null && ("auction.buy-now.v1".equals(inbox.getEventType())
                || inbox.getEventType().startsWith("order."))) {
            orderRealtimeStateProjection.ifPresent(projection -> projection.markProjectionError(inbox.getAuctionId()));
        }
        return firstError;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordProjectionAttempt(String streamId) {
        inboxRepository.findByStreamId(streamId).ifPresent(inbox -> inbox.recordAttempt(clock.instant()));
    }

    private Bid apply(
            BidAcceptedStreamEvent event,
            Auction auction,
            Bid currentLeadingBid
    ) {
        if (event.auctionVersion() <= auction.getLastBidEventVersion()) {
            return null;
        }
        if (!auction.isNextBidEventVersion(event.auctionVersion())) {
            throw new BidStreamVersionGapException(
                    "경매 입찰 이벤트 버전이 연속적이지 않습니다. auctionId=%d eventVersion=%d lastAppliedVersion=%d"
                            .formatted(auction.getId(), event.auctionVersion(), auction.getLastBidEventVersion())
            );
        }
        validateLeadingBidder(event, currentLeadingBid);
        try {
            auction.validateStreamBid(
                    event.bidderId(), event.requestedPrice(), event.bidPrice(), event.currentPrice(), event.bidCount(), event.closeTime(), event.occurredAt(),
                    event.auctionStatus(), event.isBuyNow()
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException(exception.getMessage(), exception);
        }
        if (!auction.applyStreamBid(
                event.auctionVersion(), event.currentPrice(), event.bidCount(), event.closeTime(), event.auctionStatus()
        )) {
            return null;
        }
        applyWalletTransition(event, currentLeadingBid, auction.getId());
        if (currentLeadingBid != null) {
            currentLeadingBid.markOutbid();
        }
        Bid bid = Bid.leading(
                event.bidderId(), auction, event.bidPrice(), event.occurredAt(),
                event.idempotencyKey(), event.idempotencyRequestHash()
        );
        if (event.isBuyNow()) {
            bid.markWon();
        }
        return bid;
    }

    /** Redis에서 승인된 즉시 낙찰 결과를 주문과 도메인 테이블에 projection한다. */
    private void completeBuyNow(Auction auction, Bid winningBid, java.time.Instant occurredAt, String streamId) {
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        orderService.createFromAuctionClosed(
                auction.getId(), winningBid.getBidderId(), auction.getSellerId(), card.name(), winningBid.getBidPrice()
        );
        orderRealtimeStateProjection.ifPresent(projection -> orderRepository.findByAuctionId(auction.getId())
                .ifPresent(order -> projection.markCreatedOrderAfterCommit(order, streamId)));
    }

    private AuctionTimelineEvent archive(AuctionWalletTimelineEvent event, Integer auctionId, Long auctionVersion, String payload) {
        Integer userId = extractWalletAffectingUserId(event);
        return new AuctionTimelineEvent(
            event.streamId(), auctionId, userId, auctionVersion, event.archiveEventType(), event.schemaVersion(),
                payload, event.occurredAt(), clock.instant()
        );
    }

    /**
     * 이 이벤트가 PENDING인 동안 지갑 상태를 변경할 특정 유저가 있다면 그 userId를 반환한다.
     * {@link RedisWalletStateSeeder}의 userId 스코프 catch-up 확인이 이 이벤트를 놓치지 않도록
     * {@link WalletStateChangedStreamEvent}뿐 아니라 지갑을 hold/release/capture하는
     * {@link BidAcceptedStreamEvent}(입찰자), 정산으로 지갑을 바꾸는
     * {@link OrderStateChangedStreamEvent}(walletUserId)도 함께 채운다.
     */
    private Integer extractWalletAffectingUserId(AuctionWalletTimelineEvent event) {
        return switch (event) {
            case WalletStateChangedStreamEvent wallet -> wallet.userId();
            case BidAcceptedStreamEvent bid -> bid.bidderId();
            case OrderStateChangedStreamEvent order -> order.walletUserId();
            default -> null;
        };
    }

    private void validateLeadingBidder(BidAcceptedStreamEvent event, Bid currentLeadingBid) {
        Integer actualPreviousBidderId = currentLeadingBid == null ? null : currentLeadingBid.getBidderId();
        if (!java.util.Objects.equals(event.previousBidderId(), actualPreviousBidderId)) {
            throw new InvalidBidStreamEventException("이전 최고 입찰자 정보가 DB 상태와 일치하지 않습니다.");
        }
        if (!event.isBuyNow() && actualPreviousBidderId != null && actualPreviousBidderId.equals(event.bidderId())) {
            throw new InvalidBidStreamEventException("현재 최고 입찰자는 추가 입찰할 수 없습니다.");
        }
    }

    private void applyWalletTransition(BidAcceptedStreamEvent event, Bid currentLeadingBid, Integer auctionId) {
        Integer previousBidderId = currentLeadingBid == null ? null : currentLeadingBid.getBidderId();
        if (java.util.Objects.equals(previousBidderId, event.bidderId())) {
            // 기존 POST 경로도 같은 사용자의 즉시 낙찰에서는 기존 hold를 증액한 뒤 release하지 않는다.
            walletService.hold(event.bidderId(), auctionId, event.bidPrice());
            if (event.isBuyNow()) {
                walletService.capture(event.bidderId(), auctionId, event.bidPrice());
            }
            return;
        }
        if (previousBidderId != null && previousBidderId < event.bidderId()) {
            walletService.release(previousBidderId, auctionId);
            walletService.hold(event.bidderId(), auctionId, event.bidPrice());
        } else {
            walletService.hold(event.bidderId(), auctionId, event.bidPrice());
            if (previousBidderId != null) {
                walletService.release(previousBidderId, auctionId);
            }
        }
        if (event.isBuyNow()) {
            walletService.capture(event.bidderId(), auctionId, event.bidPrice());
        }
    }
}
