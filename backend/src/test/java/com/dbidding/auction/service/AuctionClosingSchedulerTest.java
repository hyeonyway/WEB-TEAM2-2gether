package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class AuctionClosingSchedulerTest {
    private final AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T01:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private final AuctionClosingScheduler scheduler = new AuctionClosingScheduler(auctionCloseSchedulerProcessor, clock);

    @Test
    void 스케줄러는_현재_시간_기준으로_종료_대상_경매를_닫는다() {
        scheduler.closeDueAuctions();

        verify(auctionCloseSchedulerProcessor).processDueAuctions(Instant.parse("2026-07-29T01:00:00Z"), 100);
    }

    @Test
    void 백업_스케줄러의_기본_주기는_60초다() throws NoSuchMethodException {
        Scheduled scheduled = AuctionClosingScheduler.class
                .getDeclaredMethod("closeDueAuctions")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.fixedDelayString()).isEqualTo("${auction.closing.scheduler.fixed-delay-ms:60000}");
    }
}
