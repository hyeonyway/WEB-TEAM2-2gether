package com.dbidding.card.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.dbidding.card.repository.StatisticAggregationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class DailyStatisticAggregationServiceTest {
    private final StatisticAggregationRepository repository =
            mock(StatisticAggregationRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final DailyStatisticAggregationService service =
            new DailyStatisticAggregationService(repository, clock);

    @Test
    void 서울_기준_어제의_일간_시장_요약을_순서대로_갱신한다() {
        service.aggregateYesterday();

        LocalDate yesterday = LocalDate.of(2026, 7, 27);
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
