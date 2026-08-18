package com.dbidding.auction.sse;

import com.dbidding.auction.domain.AuctionStatus;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 실제 입찰 처리 없이 경매 SSE fan-out만 재현하는 테스트 전용 발행자. {@code
 * AuctionStreamPublisher.publish()}(#569 이전엔 {@code AuctionSseConnectionManager.broadcast()}를
 * 직접 호출해 Redis publish 왕복을 건너뛰었음)를 타서, "SSE=Redis pub/sub" 프로필에서 실제
 * publish→subscribe→broadcast 경로 전체의 fan-out 비용을 측정할 수 있게 한다.
 */
@Service
@Profile("test")
@RequiredArgsConstructor
public class AuctionSseTestBidApplicationService {
    private final AuctionStreamPublisher streamPublisher;
    private final AuctionSseTestAuctionReader auctionReader;
    private final Clock clock;
    private final ConcurrentMap<Integer, SimulatedBid> simulatedBids = new ConcurrentHashMap<>();
    private final AtomicLong bidderSequence = new AtomicLong();

    public AuctionStreamPayload publishRandomBid() {
        var auction = auctionReader.findRandomActiveAuction().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "진행 중인 경매가 없습니다."));
        return publish(auction);
    }

    /** 부하테스트 시나리오가 지정한 auctionId 하나에 대해 시뮬레이션 입찰을 발행한다(#569). */
    public AuctionStreamPayload publishBidFor(Integer auctionId) {
        var auction = auctionReader.findAuction(auctionId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "경매를 찾을 수 없습니다: " + auctionId));
        return publish(auction);
    }

    private AuctionStreamPayload publish(AuctionSseTestAuctionReader.Snapshot auction) {
        SimulatedBid bid = simulatedBids.compute(auction.auctionId(), (ignored, previous) -> nextBid(auction, previous));
        var payload = new AuctionStreamPayload(
                AuctionStreamEventType.BID_PLACED, auction.auctionId(), null, null, null, null, null, null,
                bid.bidderId(), bid.previousBidderId(), null, auction.startPrice(),
                bid.currentPrice(), null, auction.bidIncrement(), bid.bidCount(),
                auction.endsAt(), AuctionStatus.valueOf(auction.status()),
                null, clock.instant(), null);
        streamPublisher.publish(payload);
        return payload;
    }

    private SimulatedBid nextBid(AuctionSseTestAuctionReader.Snapshot auction, SimulatedBid previous) {
        long sequence = bidderSequence.incrementAndGet();
        int bidderId = sequence % 3 == 0 ? 1 : 900_001 + (int) (sequence % 10);
        return new SimulatedBid(
                bidderId,
                previous == null ? auction.currentBidderId() : previous.bidderId(),
                (previous == null ? auction.currentPrice() : previous.currentPrice()) + auction.bidIncrement(),
                (previous == null ? auction.bidCount() : previous.bidCount()) + 1
        );
    }

    private record SimulatedBid(Integer bidderId, Integer previousBidderId, Long currentPrice,
                                Integer bidCount) { }
}
