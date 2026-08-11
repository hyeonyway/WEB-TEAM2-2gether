package com.dbidding.auction.stream;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionEventPublisher;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.service.WalletService;
import com.dbidding.card.dto.CardResponses.CardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.order.OrderService;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuctionBidStreamPersistenceService {
    private final AuctionBidEventInboxRepository inboxRepository;
    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WalletService walletService;
    private final OrderService orderService;
    private final CardService cardService;
    private final AuctionEventPublisher auctionEventPublisher;
    private final Clock clock;

    @Transactional
    public void persist(BidAcceptedStreamEvent event) {
        if (inboxRepository.findByStreamId(event.streamId()).isPresent()) {
            return;
        }
        Auction auction = auctionRepository.findByIdForUpdate(event.auctionId())
                .orElseThrow(() -> new InvalidBidStreamEventException("존재하지 않는 경매의 입찰 이벤트입니다: " + event.auctionId()));
        Bid currentLeadingBid = bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(
                auction.getId(), BidStatus.LEADING
        ).orElse(null);
        Bid bid = apply(event, auction, currentLeadingBid);
        inboxRepository.save(new AuctionBidEventInbox(
                event.streamId(), event.auctionId(), event.auctionVersion(), clock.instant()
        ));
        if (bid != null) {
            bidRepository.save(bid);
            if (event.isBuyNow()) {
                completeBuyNow(auction, bid, event.occurredAt());
            }
        }
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

    /** 기존 즉시 낙찰 경로의 주문 생성과 종료 이벤트를 같은 DB 트랜잭션에 포함한다. */
    private void completeBuyNow(Auction auction, Bid winningBid, java.time.Instant occurredAt) {
        CardSnapshot card = cardService.getCardSnapshot(auction.getItemId());
        orderService.createFromAuctionClosed(
                auction.getId(), winningBid.getBidderId(), auction.getSellerId(), card.name(), winningBid.getBidPrice()
        );
        auctionEventPublisher.publishClosed(new AuctionClosedEvent(
                auction.getId(), card.cardId(), card.name(), card.psaGrade(), card.language(), card.thumbnailUrl(),
                winningBid.getBidderId(), auction.getSellerId(), auction.getStartPrice(), auction.getCurrentPrice(),
                winningBid.getBidPrice(), auction.getBidPriceUnit(), auction.getBidCount(), auction.getCloseTime(),
                auction.getStatus(), occurredAt
        ));
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
