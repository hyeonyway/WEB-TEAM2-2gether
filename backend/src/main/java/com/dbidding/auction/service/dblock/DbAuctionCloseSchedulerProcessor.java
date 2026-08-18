package com.dbidding.auction.service.dblock;

import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionDueClosingService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
@RequiredArgsConstructor
class DbAuctionCloseSchedulerProcessor implements AuctionCloseSchedulerProcessor {
    private final AuctionDueClosingService auctionDueClosingService;

    @Override
    public List<Integer> processDueAuctions(Instant now, int limit) {
        return auctionDueClosingService.closeDueAuctions(now, limit).stream()
                .map(response -> response.auctionId())
                .toList();
    }
}
