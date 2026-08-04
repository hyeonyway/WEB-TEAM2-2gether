package com.dbidding.statistic.service;

import com.dbidding.statistic.repository.StatisticAggregationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DailyStatisticAggregationService {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final ZoneId UTC = ZoneOffset.UTC;
    private final StatisticAggregationRepository repository;

    @Transactional
    public void aggregate(LocalDate date) {
        LocalDateTime fromUtc = date.atStartOfDay(SEOUL)
                .withZoneSameInstant(UTC)
                .toLocalDateTime();
        LocalDateTime toUtc = date.plusDays(1).atStartOfDay(SEOUL)
                .withZoneSameInstant(UTC)
                .toLocalDateTime();
        repository.aggregateItems(date, fromUtc, toUtc);
        repository.aggregateMarket(date, fromUtc, toUtc);
        repository.refreshRollingSnapshots(date.minusDays(29), date);
        repository.refreshChangeRates(date.minusDays(1), date.minusDays(7), date.minusDays(30));
    }
}
