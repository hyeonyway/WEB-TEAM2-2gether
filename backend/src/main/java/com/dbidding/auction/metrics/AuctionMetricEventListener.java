package com.dbidding.auction.metrics;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuctionMetricEventListener {

    private static final String CLOSE_TIME_EXTENDED = "close_time_extended";

    private final AuctionMetrics auctionMetrics;

    // fallbackExecution=true: 위 AuctionDeadlineScheduler.reschedule과 동일한 이유(#281 이후
    // 입찰 경로에서 트랜잭션 밖에서도 발행됨).
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void recordExtension(AuctionCloseScheduleChangedEvent event) {
        if (CLOSE_TIME_EXTENDED.equals(event.reason())) {
            auctionMetrics.recordExtension();
        }
    }
}
