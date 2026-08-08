package com.dbidding.auction.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class AuctionClosingSchedulerTest {
    private final AuctionCommandService auctionCommandService = mock(AuctionCommandService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final AuctionClosingScheduler scheduler = new AuctionClosingScheduler(auctionCommandService, clock);

    @Test
    void 스케줄러는_현재_시간_기준으로_종료_대상_경매를_닫는다() {
        scheduler.closeDueAuctions();

        verify(auctionCommandService).closeDueAuctions(Instant.parse("2026-07-29T01:00:00Z"), 100);
    }
}
