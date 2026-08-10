package com.dbidding.auction.stream;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
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
        if (!auction.applyStreamBid(
                event.auctionVersion(), event.currentPrice(), event.bidCount(), event.closeTime(), event.auctionStatus()
        )) {
            return;
        }
        Bid currentLeadingBid = currentLeadingBids.get(auction.getId());
        if (currentLeadingBid != null) {
            currentLeadingBid.markOutbid();
        }
        Bid bid = Bid.leading(
                event.bidderId(), auction, event.bidPrice(), event.occurredAt(),
                event.idempotencyKey(), event.idempotencyRequestHash()
        );
        currentLeadingBids.put(auction.getId(), bid);
        bids.add(bid);
    }
}
