package com.dbidding.card.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.dbidding.card.repository.StatisticAggregationRepository;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DailyStatisticAggregationServiceTest {
    private final StatisticAggregationRepository repository =
            mock(StatisticAggregationRepository.class);
    private final DailyStatisticAggregationService service =
            new DailyStatisticAggregationService(repository);

    @Test
    void 서울_기준_어제의_일간_시장_요약을_순서대로_갱신한다() {
        LocalDate yesterday = LocalDate.of(2026, 7, 27);
        service.aggregate(yesterday);
        InOrder order = inOrder(repository);
        order.verify(repository).aggregateItems(
                yesterday, yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay());
        order.verify(repository).aggregateMarket(
                yesterday, yesterday.atStartOfDay(), yesterday.plusDays(1).atStartOfDay());
        order.verify(repository).refreshRollingSnapshots(
                yesterday.minusDays(29), yesterday);
        order.verify(repository).refreshChangeRates(
                yesterday.minusDays(1), yesterday.minusDays(7), yesterday.minusDays(30));
    }
}
