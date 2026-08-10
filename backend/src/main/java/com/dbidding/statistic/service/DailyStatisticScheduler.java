package com.dbidding.statistic.service;

import com.dbidding.statistic.repository.MarketDailyStatisticRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "statistic.scheduler.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class DailyStatisticScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final DailyStatisticAggregationService aggregationService;
    private final MarketDailyStatisticRepository marketStatisticRepository;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    @SchedulerLock(
            name = "daily-statistic-aggregation",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT30M"
    )
    public void aggregateOnStartup() {
        aggregateMissingDates();
    }

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @SchedulerLock(
            name = "daily-statistic-aggregation",
            lockAtLeastFor = "PT1M",
            lockAtMostFor = "PT30M"
    )
    public void aggregateOnSchedule() {
        aggregateMissingDates();
    }

    public void aggregateMissingDates() {
        LocalDate yesterday = LocalDate.now(clock.withZone(SEOUL)).minusDays(1);
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
