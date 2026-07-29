package com.dbidding.card.service;

import com.dbidding.home.repository.MarketDailyStatisticRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "statistics.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DailyStatisticScheduler {
    private final DailyStatisticAggregationService aggregationService;
    private final MarketDailyStatisticRepository marketStatisticRepository;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void aggregateOnStartup() {
        aggregateMissingDates();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    public void aggregateOnSchedule() {
        aggregateMissingDates();
    }

    public void aggregateMissingDates() {
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate firstMissingDate = marketStatisticRepository
                .findFirstByOrderByStatisticsDateDesc()
                .map(statistic -> statistic.getStatisticsDate().plusDays(1))
                .orElse(yesterday);

        for (LocalDate date = firstMissingDate; !date.isAfter(yesterday); date = date.plusDays(1)) {
            try {
                aggregationService.aggregate(date);
                log.info("Completed daily statistics aggregation for {}", date);
            } catch (RuntimeException exception) {
                log.error("Stopped daily statistics catch-up at {}", date, exception);
                throw exception;
            }
        }
    }
}
