package com.dbidding.card.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.dbidding.statistic.repository.StatisticAggregationRepository;
import com.dbidding.statistic.service.DailyStatisticAggregationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DailyStatisticAggregationServiceTest {
    private final StatisticAggregationRepository repository =
            mock(StatisticAggregationRepository.class);
    private final DailyStatisticAggregationService service =
            new DailyStatisticAggregationService(repository);

    @Test
    void 서울_기준_어제의_일간_시장_요약을_순서대로_갱신한다() {
        LocalDate date = LocalDate.of(2026, 8, 4);
        service.aggregate(date);
        InOrder order = inOrder(repository);
        order.verify(repository).aggregateItems(
                date,
                LocalDateTime.of(2026, 8, 3, 15, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0));
        order.verify(repository).aggregateMarket(
                date,
                LocalDateTime.of(2026, 8, 3, 15, 0),
                LocalDateTime.of(2026, 8, 4, 15, 0));
        order.verify(repository).refreshRollingSnapshots(
                date.minusDays(29), date);
        order.verify(repository).refreshChangeRates(
                date.minusDays(1), date.minusDays(7), date.minusDays(30));
    }
}
