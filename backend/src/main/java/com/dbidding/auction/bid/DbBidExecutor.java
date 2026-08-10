package com.dbidding.auction.bid;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.metrics.AuctionMetrics.BidStep;
import com.dbidding.auction.metrics.AuctionMetrics.LockOperation;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.order.OrderService;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 {@code AuctionCommandService.participateInternal()}(DB row lock 기반)을 그대로 이관한
 * 구현체. 즉시구매(buyNow) 시 낙찰 처리({@code closeLockedAuction} 계열)는 마감 스케줄러
 * 경로({@code AuctionCommandService})와 공유하지 않고 이 클래스에 복제되어 있다 — 마감
 * 스케줄러는 이 작업 범위 밖이라 건드리지 않기 위한 의도적인 중복이다.
 */
@Service
@Profile("!redis")
@RequiredArgsConstructor
@Slf4j
public class DbBidExecutor implements BidExecutor {
    private static final Duration BID_EXTENSION_WINDOW = Duration.ofMinutes(5);
    private static final Duration BID_EXTENSION_DURATION = Duration.ofMinutes(5);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final AuctionEventPublisher auctionEventPublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final CardService cardService;
    private final OrderService orderService;
    private final Clock clock;
    private final AuctionMetrics auctionMetrics;

    @Override
    @Transactional
    public BidResponses.BidResult execute(BidCommand command) {
        IdempotencyKeys.validate(command.idempotencyKey());
        String requestHash = IdempotencyKeys.sha256(command.price());
        Integer auctionId = command.auctionId();
        Integer userId = command.bidderId();
        Auction auction = findByIdForUpdate(auctionId)
                .orElseThrow(AuctionException::notFound);
        Timer.Sample criticalSection = auctionMetrics.startBidCriticalSection();
        try {
            Optional<BidResponses.BidResult> idempotentResponse = findIdempotentBidResponse(
                    userId,
                    auctionId,
                    command.idempotencyKey(),
                    requestHash,
                    auction
            );
            if (idempotentResponse.isPresent()) {
                return idempotentResponse.get();
            }

            validateNotSellerBid(userId, auction);
            long bidPrice = bidPrice(auction, command.price());
            boolean buyNow = isBuyNowBid(auction, command.price());
            Bid previousLeadingBid = highestBid(auction.getId()).orElse(null);
            if (!buyNow) {
                validateNotCurrentLeadingBidder(userId, previousLeadingBid, auction.getId());
            }

            Instant bidAt = now();
            Instant previousCloseTime = auction.getCloseTime();
            boolean closeTimeExtended = placeBid(auction, bidPrice, bidAt);
            WalletBalanceResponse wallet;
            if (shouldReleasePreviousHoldFirst(previousLeadingBid, userId)) {
                if (previousLeadingBid != null) {
                    auctionMetrics.recordBidStep(BidStep.OUTBID,
                            () -> outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt));
                }
                wallet = auctionMetrics.recordBidStep(BidStep.HOLD,
                        () -> walletService.hold(userId, auction.getId(), bidPrice));
            } else {
                wallet = auctionMetrics.recordBidStep(BidStep.HOLD,
                        () -> walletService.hold(userId, auction.getId(), bidPrice));
                if (previousLeadingBid != null) {
                    auctionMetrics.recordBidStep(BidStep.OUTBID,
                            () -> outbidPreviousLeadingBid(previousLeadingBid, userId, auction, bidAt));
                }
            }

            Bid currentLeadingBid = auctionMetrics.recordBidStep(BidStep.SAVE, () -> bidRepository.save(Bid.leading(
                    userId, auction, bidPrice, bidAt, command.idempotencyKey(), requestHash
            )));
            publishBidPlaced(auction, userId, auction.getItemId(), previousLeadingBid, bidAt);
            if (buyNow) {
                closeLockedAuction(auction, bidAt);
            }
            log.info(
                    "event=auction.bid.accepted auctionId={} bidderId={} bidId={} bidPrice={} currentPrice={} bidCount={} previousLeadingBidId={} closeTimeExtended={} previousCloseTime={} currentCloseTime={} status={}",
                    auction.getId(), userId, currentLeadingBid.getId(), command.price(), auction.getCurrentPrice(),
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

            Timer.Sample flush = auctionMetrics.startBidFlush();
            try {
                auctionRepository.flush();
            } finally {
                auctionMetrics.finishBidFlush(flush);
            }
            return bidResult(currentLeadingBid, auction, wallet);
        } finally {
            auctionMetrics.finishBidCriticalSection(criticalSection);
        }
    }

    private long bidPrice(Auction auction, long requestedPrice) {
        Long buyNowPrice = auction.getBuyNowPrice();
        return buyNowPrice != null && requestedPrice >= buyNowPrice ? buyNowPrice : requestedPrice;
    }

    private boolean isBuyNowBid(Auction auction, long requestedPrice) {
        Long buyNowPrice = auction.getBuyNowPrice();
        return buyNowPrice != null && requestedPrice >= buyNowPrice;
    }

    private Optional<Auction> findByIdForUpdate(Integer auctionId) {
        Timer.Sample sample = auctionMetrics.start();
        try {
            return auctionRepository.findByIdForUpdate(auctionId);
        } finally {
            auctionMetrics.finishAuctionLockWait(sample, LockOperation.BID);
        }
    }

    private void validateNotSellerBid(Integer userId, Auction auction) {
        if (auction.getSellerId().equals(userId)) {
            log.warn("event=auction.bid.rejected_self_bid auctionId={} sellerId={} bidderId={}",
                    auction.getId(), auction.getSellerId(), userId);
            throw AuctionException.sellerBidForbidden();
        }
    }

    private void validateNotCurrentLeadingBidder(Integer userId, Bid previousLeadingBid, Integer auctionId) {
        if (previousLeadingBid != null && previousLeadingBid.getBidderId().equals(userId)) {
            log.warn("event=auction.bid.rejected_leading_bidder auctionId={} bidderId={}", auctionId, userId);
            throw AuctionException.leadingBidderConflict();
        }
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
            throw AuctionException.idempotencyConflict();
        }
        return Optional.of(bidResult(bid, auction, walletService.getBalance(bidderId)));
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
            throw AuctionException.invalidBidRequest(exception.getMessage());
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
}
