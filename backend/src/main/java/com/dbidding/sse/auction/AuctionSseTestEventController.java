package com.dbidding.sse.auction;

import com.dbidding.sse.auction.AuctionSseTestAuctionReader.Snapshot;
import com.dbidding.sse.auction.payload.AuctionPayloadStatus;
import com.dbidding.sse.auction.payload.BidPlacedPayload;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auctions/stream/test-events")
@RequiredArgsConstructor
public class AuctionSseTestEventController {

    private final ApplicationEventPublisher eventPublisher;
    private final AuctionSseTestAuctionReader auctionReader;
    private final ConcurrentMap<Integer, SimulatedBid> simulatedBids = new ConcurrentHashMap<>();
    private final AtomicLong bidderSequence = new AtomicLong();

    @PostMapping("/random-bid")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BidPlacedPayload publishRandomBid() {
        Snapshot auction = auctionReader.findRandomActiveAuction()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "진행 중인 경매가 없습니다."
                ));
        SimulatedBid bid = simulatedBids.compute(auction.auctionId(), (auctionId, previous) ->
                nextBid(auction, previous)
        );
        BidPlacedPayload payload = toPayload(auction, bid);
        eventPublisher.publishEvent(payload);
        return payload;
    }

    private SimulatedBid nextBid(Snapshot auction, SimulatedBid previous) {
        long currentPrice = previous == null
                ? auction.currentPrice()
                : Math.max(previous.currentPrice(), auction.currentPrice());
        int bidCount = previous == null
                ? auction.bidCount()
                : Math.max(previous.bidCount(), auction.bidCount());
        long version = previous == null
                ? auction.auctionVersion()
                : Math.max(previous.auctionVersion(), auction.auctionVersion());
        Integer previousBidderId = previous == null
                ? auction.currentBidderId()
                : previous.bidderId();
        int bidderId = bidderSequence.incrementAndGet() % 3 == 0
                ? 1
                : 900_001 + (int) (bidderSequence.get() % 10);

        return new SimulatedBid(
                bidderId,
                previousBidderId,
                currentPrice + auction.bidIncrement(),
                bidCount + 1,
                version + 1
        );
    }

    private BidPlacedPayload toPayload(Snapshot auction, SimulatedBid bid) {
        return new BidPlacedPayload(
                null,
                auction.auctionId(),
                bid.bidderId(),
                bid.previousBidderId(),
                auction.startPrice(),
                bid.currentPrice(),
                auction.bidIncrement(),
                bid.bidCount(),
                auction.endsAt(),
                AuctionPayloadStatus.valueOf(auction.status()),
                bid.auctionVersion(),
                LocalDateTime.now()
        );
    }

    private record SimulatedBid(
            Integer bidderId,
            Integer previousBidderId,
            Long currentPrice,
            Integer bidCount,
            Long auctionVersion
    ) {
    }
}
