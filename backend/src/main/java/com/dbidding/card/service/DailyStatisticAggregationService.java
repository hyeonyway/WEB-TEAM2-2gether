package com.dbidding.card.service;

import com.dbidding.card.repository.StatisticAggregationRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyStatisticAggregationService {
    private final StatisticAggregationRepository repository;
    private final Clock clock;

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Seoul")
    @Transactional
    public void aggregateYesterday() {
        aggregate(LocalDate.now(clock).minusDays(1));
    }

    @Transactional
    public void aggregate(LocalDate date) {
        repository.aggregateItems(date, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        repository.aggregateMarket(date, date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        repository.refreshRollingSnapshots(date.minusDays(29), date);
        repository.refreshChangeRates(date.minusDays(1), date.minusDays(7), date.minusDays(30));
    }
}
