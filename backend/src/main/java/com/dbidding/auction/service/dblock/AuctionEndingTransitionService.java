package com.dbidding.auction.service.dblock;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.metrics.AuctionMetrics;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.AuctionEndingPolicy;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import com.dbidding.auction.service.EndingExtensionProvider;
import com.dbidding.auction.sse.AuctionStreamPayload;
import com.dbidding.auction.sse.AuctionStreamPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile("!redis")
@RequiredArgsConstructor
public class AuctionEndingTransitionService implements AuctionEndingTransitionProcessor {
    private final AuctionRepository auctionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final AuctionStreamPublisher auctionStreamPublisher;
    private final AuctionMetrics auctionMetrics;
    private final EndingExtensionProvider extensionProvider;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean transitionIfDue(Integer auctionId, Instant now) {
        return auctionRepository.findByIdForUpdate(auctionId)
                .filter(auction -> auction.getStatus() == AuctionStatus.OPEN)
                .filter(auction -> now.isBefore(auction.getCloseTime()))
                .filter(auction -> !auction.getCloseTime().minus(AuctionEndingPolicy.WINDOW).isAfter(now))
                .map(auction -> transition(auction, now))
                .orElse(false);
    }

    @Override
    public List<Integer> transitionDueAuctions(Instant now, int limit) {
        return auctionRepository.findDueAuctionIds(
                        List.of(AuctionStatus.OPEN), now.plus(AuctionEndingPolicy.WINDOW), PageRequest.of(0, limit)
                ).stream()
                .filter(auctionId -> transitionIfDue(auctionId, now))
                .toList();
    }

    private boolean transition(Auction auction, Instant now) {
        Duration randomExtension = extensionProvider.next();
        if (!auction.enterEnding(randomExtension)) {
            return false;
        }
        auctionMetrics.recordEndingTransition();
        log.info(
                "event=auction.ending.transitioned auctionId={} estimatedCloseTime={} realCloseTime={} extensionSeconds={}",
                auction.getId(), auction.getEstimatedCloseTime(), auction.getCloseTime(), randomExtension.toSeconds()
        );
        eventPublisher.publishEvent(new AuctionCloseScheduleChangedEvent(
                auction.getId(), auction.getCloseTime(), "ending_transition"
        ));
        auctionStreamPublisher.publish(AuctionStreamPayload.endingStarted(auction, now));
        return true;
    }
}
