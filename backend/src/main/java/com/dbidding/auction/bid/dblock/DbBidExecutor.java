package com.dbidding.auction.bid.dblock;

import com.dbidding.auction.IdempotencyKeys;
import com.dbidding.auction.bid.dto.AuctionCloseData;
import com.dbidding.auction.bid.dto.BidCommand;
import com.dbidding.auction.bid.dto.BidEventData;
import com.dbidding.auction.bid.dto.BidExecutionResult;
import com.dbidding.auction.bid.BidExecutor;
import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.exception.AuctionException;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.metrics.AuctionMetrics.BidStep;
import com.dbidding.auction.metrics.AuctionMetrics.LockOperation;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.order.service.OrderService;
import com.dbidding.wallet.dto.WalletBalanceResponse;
import com.dbidding.wallet.service.WalletService;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기존 {@code AuctionCommandService.participateInternal()}(DB row lock 기반)을 그대로 이관한
 * 구현체. 즉시구매(buyNow) 시 낙찰 처리({@code closeLockedAuction} 계열)는 마감 스케줄러
 * 경로({@code AuctionCommandService})와 공유하지 않고 이 클래스에 복제되어 있다 — 마감
 * 스케줄러는 이 작업 범위 밖이라 건드리지 않기 위한 의도적인 중복이다.
 *
 * <p>이벤트 발행은 전혀 하지 않는다(#281) — 입찰 가능여부 판단, wallet 처리, (buyNow 시)
 * Bid/Order 등 원자적 쓰기만 담당하고, 이벤트 조립·발행은 {@code AuctionCommandService}가
 * 이 클래스의 반환값({@link BidExecutionResult})으로부터 한다. {@code RedisBidExecutor}(Lua
 * 기반)는 애초에 Lua 안에서 publish를 할 수 없으므로, 두 구현체가 동일한 계약을 갖기 위한
 * 정리다.
 */
@Service
@Profile("!redis")
@RequiredArgsConstructor
@Slf4j
public class DbBidExecutor implements BidExecutor {
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final CardService cardService;
    private final OrderService orderService;
    private final Clock clock;
    private final AuctionMetrics auctionMetrics;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BidExecutionResult execute(BidCommand command) {
        IdempotencyKeys.validate(command.idempotencyKey());
        String requestHash = IdempotencyKeys.sha256(command.price());
        Integer auctionId = command.auctionId();
        Integer userId = command.bidderId();
        Auction auction = findByIdForUpdate(auctionId)
                .orElseThrow(AuctionException::notFound);
        Timer.Sample criticalSection = auctionMetrics.startBidCriticalSection();
        try {
            Optional<BidExecutionResult> idempotentResponse = findIdempotentBidResponse(
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
            } else {
                walletService.lockWalletsInOrder(
                        userId,
                        previousLeadingBid == null ? null : previousLeadingBid.getBidderId(),
                        auction.getSellerId()
                );
            }

            Instant bidAt = now();
            placeBid(auction, bidPrice, bidAt);
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
            AuctionCloseData closeData = buyNow ? closeLockedAuction(auction, bidAt) : null;
            log.info(
                    "event=auction.bid.accepted auctionId={} bidderId={} bidId={} bidPrice={} currentPrice={} bidCount={} previousLeadingBidId={} closeTime={} status={}",
                    auction.getId(), userId, currentLeadingBid.getId(), command.price(), auction.getCurrentPrice(),
                    auction.getBidCount(), previousLeadingBid == null ? null : previousLeadingBid.getId(),
                    auction.getCloseTime(), auction.getStatus()
            );

            Timer.Sample flush = auctionMetrics.startBidFlush();
            try {
                auctionRepository.flush();
            } finally {
                auctionMetrics.finishBidFlush(flush);
            }
            BidEventData eventData = new BidEventData(
                    auction.getItemId(),
                    previousLeadingBid == null ? null : previousLeadingBid.getBidderId(),
                    previousLeadingBid == null ? null : previousLeadingBid.getId(),
                    auction.getStartPrice(),
                    auction.getBidPriceUnit(),
                    auction.getStatus(),
                    closeData
            );
            return new BidExecutionResult(bidResult(currentLeadingBid, auction, wallet), eventData);
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

    private Optional<BidExecutionResult> findIdempotentBidResponse(
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
        BidResponses.BidResult result = bidResult(bid, auction, walletService.getBalance(bidderId));
        return Optional.of(new BidExecutionResult(result, null));
    }

    private void placeBid(Auction auction, Long price, Instant bidAt) {
        try {
            auction.placeBid(price, bidAt);
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

    private AuctionCloseData closeLockedAuction(Auction auction, Instant closedAt) {
        Optional<Bid> winningBid = highestBid(auction.getId());
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        AuctionCloseData closeData = new AuctionCloseData(
                card.cardId(), card.name(), card.psaGrade(), card.language(), card.thumbnailUrl(),
                auction.getSellerId()
        );
        if (winningBid.isEmpty()) {
            auction.closeWithoutTrade(closedAt);
            log.info("event=auction.closed.without_trade auctionId={} itemId={} sellerId={} closedAt={} status={} bidCount={}",
                    auction.getId(), auction.getItemId(), auction.getSellerId(), closedAt,
                    auction.getStatus(), auction.getBidCount());
            return closeData;
        }

        Bid winner = winningBid.get();
        winner.markWon();
        auction.closeWithWinningBid(winner, closedAt);
        walletService.captureAfterHold(winner.getBidderId(), auction.getId(), winner.getBidPrice());
        orderService.createFromAuctionClosed(
                auction.getId(), winner.getBidderId(), auction.getSellerId(), card.name(), winner.getBidPrice()
        );
        log.info(
                "event=auction.closed.with_winner auctionId={} itemId={} sellerId={} winnerId={} winningBidId={} winningPrice={} closedAt={} status={} bidCount={}",
                auction.getId(), auction.getItemId(), auction.getSellerId(), winner.getBidderId(), winner.getId(),
                winner.getBidPrice(), closedAt, auction.getStatus(), auction.getBidCount()
        );
        return closeData;
    }

    private Optional<Bid> highestBid(Integer auctionId) {
        return bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auctionId, BidStatus.LEADING);
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
                        auction.getStatus() == com.dbidding.auction.domain.AuctionStatus.OPEN
                                || auction.getStatus() == com.dbidding.auction.domain.AuctionStatus.ENDING
                                ? auction.getEstimatedCloseTime() : auction.getCloseTime()
                ),
                new BidResponses.WalletSummary(wallet.availableBalance(), wallet.frozenBalance())
        );
    }

    private Instant now() {
        return clock.instant();
    }
}
