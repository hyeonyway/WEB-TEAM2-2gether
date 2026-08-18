package com.dbidding.auction.service.dblock;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.service.AuctionCloseScheduleChangedEvent;
import com.dbidding.auction.service.AuctionCloseSchedulerProcessor;
import com.dbidding.auction.service.AuctionEndingTransitionProcessor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class DbAuctionDeadlineSchedulerTransactionTest {
    @Test
    void 일정_변경은_커밋_후에만_재예약하고_롤백하면_무시한다() {
        AuctionCloseSchedulerProcessor auctionCloseSchedulerProcessor = mock(AuctionCloseSchedulerProcessor.class);
        AuctionRepository auctionRepository = mock(AuctionRepository.class);
        AuctionEndingTransitionProcessor auctionEndingTransitionProcessor = mock(AuctionEndingTransitionProcessor.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        Clock clock = Clock.systemUTC();

        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1))).thenReturn(List.of());
        when(auctionRepository.findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1))).thenReturn(List.of());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(TransactionalEventListenerFactory.class);
            context.registerBean(DbAuctionDeadlineScheduler.class, () -> new DbAuctionDeadlineScheduler(
                    auctionCloseSchedulerProcessor,
                    auctionRepository,
                    auctionEndingTransitionProcessor,
                    taskScheduler,
                    clock
            ));
            context.refresh();

            TransactionTemplate transaction = new TransactionTemplate(new TestTransactionManager());
            AuctionCloseScheduleChangedEvent event = new AuctionCloseScheduleChangedEvent(
                    1,
                    Instant.parse("2026-07-29T10:00:00Z"),
                    "auction_created"
            );

            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                verifyNoInteractions(auctionRepository);
            });

            verify(auctionRepository).findNextCloseTarget(List.of(AuctionStatus.OPEN), PageRequest.of(0, 1));
            verify(auctionRepository).findNextCloseTarget(List.of(AuctionStatus.ENDING), PageRequest.of(0, 1));

            reset(auctionRepository);
            transaction.executeWithoutResult(status -> {
                context.publishEvent(event);
                status.setRollbackOnly();
            });

            verifyNoInteractions(auctionRepository);
        }
    }

    private static class TestTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
