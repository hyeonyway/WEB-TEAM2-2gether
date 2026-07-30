package com.dbidding.statistic.repository;

import com.dbidding.statistic.domain.MarketDailyStatistic;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketDailyStatisticRepository
        extends JpaRepository<MarketDailyStatistic, LocalDate> {
    List<MarketDailyStatistic> findByStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
            LocalDate from, LocalDate to);

    Optional<MarketDailyStatistic> findFirstByOrderByStatisticsDateDesc();
}
