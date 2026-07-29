package com.dbidding.card.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.dbidding.home.domain.MarketDailyStatistic;
import com.dbidding.home.repository.MarketDailyStatisticRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DailyStatisticSchedulerTest {
    private final DailyStatisticAggregationService aggregationService =
            mock(DailyStatisticAggregationService.class);
    private final MarketDailyStatisticRepository repository =
            mock(MarketDailyStatisticRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-29T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final DailyStatisticScheduler scheduler =
            new DailyStatisticScheduler(aggregationService, repository, clock);

    @Test
    void 통계가_7월_27일까지_있으면_누락된_7월_28일을_보충한다() {
        MarketDailyStatistic latest = mock(MarketDailyStatistic.class);
        given(latest.getStatisticsDate()).willReturn(LocalDate.of(2026, 7, 27));
        given(repository.findFirstByOrderByStatisticsDateDesc()).willReturn(Optional.of(latest));

        scheduler.aggregateMissingDates();

        org.mockito.Mockito.verify(aggregationService).aggregate(LocalDate.of(2026, 7, 28));
    }

    @Test
    void 통계가_비어_있으면_어제만_집계한다() {
        given(repository.findFirstByOrderByStatisticsDateDesc()).willReturn(Optional.empty());

        scheduler.aggregateMissingDates();

        org.mockito.Mockito.verify(aggregationService).aggregate(LocalDate.of(2026, 7, 28));
    }

    @Test
    void 어제까지_완료되었으면_집계하지_않는다() {
        MarketDailyStatistic latest = mock(MarketDailyStatistic.class);
        given(latest.getStatisticsDate()).willReturn(LocalDate.of(2026, 7, 28));
        given(repository.findFirstByOrderByStatisticsDateDesc()).willReturn(Optional.of(latest));

        scheduler.aggregateMissingDates();

        verifyNoInteractions(aggregationService);
    }
}
