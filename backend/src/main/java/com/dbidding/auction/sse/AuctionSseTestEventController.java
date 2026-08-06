package com.dbidding.auction.sse;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.global.time.UtcTime;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("test")
@RequestMapping("/api/auctions/stream/test-events")
@RequiredArgsConstructor
public class AuctionSseTestEventController {
    private final AuctionSseConnectionManager connectionManager;
    private final AuctionSseTestAuctionReader auctionReader;
    private final Clock clock;
    private final ConcurrentMap<Integer, SimulatedBid> simulatedBids = new ConcurrentHashMap<>();
    private final AtomicLong bidderSequence = new AtomicLong();

    @PostMapping("/random-bid")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AuctionStreamPayload publishRandomBid() {
        var auction = auctionReader.findRandomActiveAuction().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 경매가 없습니다."));
        SimulatedBid bid = simulatedBids.compute(auction.auctionId(), (ignored, previous) -> nextBid(auction, previous));
        var payload = new AuctionStreamPayload(
                AuctionStreamEventType.BID_PLACED, auction.auctionId(), null, null, null, null, null, null,
                bid.bidderId(), bid.previousBidderId(), null, auction.startPrice(),
                bid.currentPrice(), null, auction.bidIncrement(), bid.bidCount(),
                UtcTime.toInstant(auction.endsAt()), AuctionStatus.valueOf(auction.status()),
                bid.auctionVersion(), null, clock.instant());
        connectionManager.broadcast(payload);
        return payload;
    }

    private SimulatedBid nextBid(AuctionSseTestAuctionReader.Snapshot auction, SimulatedBid previous) {
        long sequence = bidderSequence.incrementAndGet();
        int bidderId = sequence % 3 == 0 ? 1 : 900_001 + (int) (sequence % 10);
        return new SimulatedBid(
                bidderId,
                previous == null ? auction.currentBidderId() : previous.bidderId(),
                (previous == null ? auction.currentPrice() : previous.currentPrice()) + auction.bidIncrement(),
                (previous == null ? auction.bidCount() : previous.bidCount()) + 1,
                (previous == null ? auction.auctionVersion() : previous.auctionVersion()) + 1);
    }

    private record SimulatedBid(Integer bidderId, Integer previousBidderId, Long currentPrice,
                                Integer bidCount, Long auctionVersion) { }
}
