package com.dbidding.auction.stream;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.wallet.service.WalletService;
import java.time.Clock;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final Clock clock;

    @Transactional
    public void persistAll(List<BidAcceptedStreamEvent> events) {
        if (events.isEmpty()) {
            return;
        }
        List<BidAcceptedStreamEvent> newEvents = excludeProcessed(events);
        if (newEvents.isEmpty()) {
            return;
        }
        Map<Integer, Auction> auctions = lockAuctions(newEvents);
        Map<Integer, Bid> currentLeadingBids = currentLeadingBids(auctions.keySet());
        List<AuctionBidEventInbox> inboxes = newEvents.stream()
                .map(event -> new AuctionBidEventInbox(
                        event.streamId(), event.auctionId(), event.auctionVersion(), clock.instant()
                ))
                .toList();
        List<Bid> bids = new java.util.ArrayList<>();

        newEvents.stream()
                .sorted(Comparator.comparing(BidAcceptedStreamEvent::auctionId)
                        .thenComparing(BidAcceptedStreamEvent::auctionVersion))
                .forEach(event -> apply(event, auctions.get(event.auctionId()), currentLeadingBids, bids));

        inboxRepository.saveAll(inboxes);
        bidRepository.saveAll(bids);
    }

    private List<BidAcceptedStreamEvent> excludeProcessed(List<BidAcceptedStreamEvent> events) {
        Set<String> processedIds = inboxRepository.findByStreamIdIn(events.stream()
                        .map(BidAcceptedStreamEvent::streamId)
                        .toList())
                .stream()
                .map(AuctionBidEventInbox::getStreamId)
                .collect(Collectors.toSet());
        return events.stream().filter(event -> !processedIds.contains(event.streamId())).toList();
    }

    private Map<Integer, Auction> lockAuctions(Collection<BidAcceptedStreamEvent> events) {
        Map<Integer, Auction> auctions = auctionRepository.findByIdInForUpdate(events.stream()
                        .map(BidAcceptedStreamEvent::auctionId)
                        .collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Auction::getId, auction -> auction));
        events.stream()
                .map(BidAcceptedStreamEvent::auctionId)
                .filter(id -> !auctions.containsKey(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new InvalidBidStreamEventException("존재하지 않는 경매의 입찰 이벤트입니다: " + id);
                });
        return auctions;
    }

    private Map<Integer, Bid> currentLeadingBids(Collection<Integer> auctionIds) {
        return bidRepository.findByAuctionIdInAndStatus(auctionIds, BidStatus.LEADING).stream()
                .collect(Collectors.toMap(bid -> bid.getAuction().getId(), bid -> bid));
    }

    private void apply(
            BidAcceptedStreamEvent event,
            Auction auction,
            Map<Integer, Bid> currentLeadingBids,
            List<Bid> bids
    ) {
        if (event.auctionVersion() <= auction.getLastBidEventVersion()) {
            return;
        }
        if (!auction.isNextBidEventVersion(event.auctionVersion())) {
            throw new InvalidBidStreamEventException(
                    "경매 입찰 이벤트 버전이 연속적이지 않습니다. auctionId=%d eventVersion=%d lastAppliedVersion=%d"
                            .formatted(auction.getId(), event.auctionVersion(), auction.getLastBidEventVersion())
            );
        }
        validateLeadingBidder(event, currentLeadingBids.get(auction.getId()));
        try {
            auction.validateStreamBid(
                    event.bidderId(), event.bidPrice(), event.currentPrice(), event.bidCount(), event.closeTime(), event.occurredAt(),
                    event.auctionStatus(), event.isBuyNow()
            );
        } catch (IllegalArgumentException exception) {
            throw new InvalidBidStreamEventException(exception.getMessage(), exception);
        }
        if (!auction.applyStreamBid(
                event.auctionVersion(), event.currentPrice(), event.bidCount(), event.closeTime(), event.auctionStatus()
        )) {
            return;
        }
        Bid currentLeadingBid = currentLeadingBids.get(auction.getId());
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
            currentLeadingBids.remove(auction.getId());
        } else {
            currentLeadingBids.put(auction.getId(), bid);
        }
        bids.add(bid);
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
