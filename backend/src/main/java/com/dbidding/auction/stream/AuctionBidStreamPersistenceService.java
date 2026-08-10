package com.dbidding.auction.stream;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionBidEventInbox;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionBidEventInboxRepository;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import java.time.Clock;
import java.util.List;
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
        events.forEach(this::persist);
    }

    private void persist(BidAcceptedStreamEvent event) {
        if (inboxRepository.findByStreamId(event.streamId()).isPresent()) {
            return;
        }
        Auction auction = auctionRepository.findByIdForUpdate(event.auctionId())
                .orElseThrow(() -> new InvalidBidStreamEventException("존재하지 않는 경매의 입찰 이벤트입니다."));
        inboxRepository.save(new AuctionBidEventInbox(
                event.streamId(), event.auctionId(), event.auctionVersion(), clock.instant()
        ));
        if (!auction.applyStreamBid(
                event.auctionVersion(), event.currentPrice(), event.bidCount(), event.closeTime(), event.auctionStatus()
        )) {
            return;
        }
        bidRepository.findFirstByAuctionIdAndStatusOrderByBidPriceDescCreatedAtAsc(auction.getId(), BidStatus.LEADING)
                .ifPresent(Bid::markOutbid);
        bidRepository.save(Bid.leading(
                event.bidderId(), auction, event.bidPrice(), event.occurredAt(),
                event.idempotencyKey(), event.idempotencyRequestHash()
        ));
    }
}
