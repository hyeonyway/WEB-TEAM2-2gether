package com.dbidding.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.dbidding.card.repository.ItemStatisticRepository;
import com.dbidding.home.repository.HomeAuctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HomeServiceTest {
    private final HomeAuctionRepository auctionRepository = mock(HomeAuctionRepository.class);
    private final ItemStatisticRepository statisticRepository = mock(ItemStatisticRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(auctionRepository, statisticRepository, clock);
    }

    @Test
    void 진행_경매로_인사이트를_집계한다() {
        var aggregate = mock(HomeAuctionRepository.InsightAggregate.class);
        given(aggregate.getTotalCount()).willReturn(11L);
        given(aggregate.getRisingCount()).willReturn(3L);
        given(aggregate.getAverageRisingRate()).willReturn(12.345);
        given(aggregate.getBidAuctionCount()).willReturn(7L);
        given(auctionRepository.aggregateInsights()).willReturn(aggregate);

        var insights = homeService.getInsights();

        assertThat(insights).extracting("id", "value")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("RISING", 3L),
                        org.assertj.core.groups.Tuple.tuple("NEW_BIDS", 7L),
                        org.assertj.core.groups.Tuple.tuple("ACTIVE", 2L)
                );
        assertThat(insights.getFirst().changeRate()).isEqualByComparingTo("12.35");
        assertThat(insights.get(1).changeRate()).isNull();
    }

    @Test
    void 종료_경매를_일별로_집계하고_무거래일에는_가격을_이월한다() {
        LocalDateTime from = LocalDate.of(2026, 6, 29).atStartOfDay();
        LocalDateTime to = LocalDate.of(2026, 7, 29).atStartOfDay();
        var yesterday = daily(LocalDate.of(2026, 7, 27), 100_000.0, 3L);
        var today = daily(LocalDate.of(2026, 7, 28), 120_000.0, 2L);
        given(auctionRepository.aggregateDailyMarket(from, to))
                .willReturn(List.of(yesterday, today));
        given(auctionRepository.findPreviousDailyAverage(from)).willReturn(java.util.Optional.of(80_000.0));

        var market = homeService.getMarket(30);

        assertThat(market.marketHistory()).hasSize(30);
        assertThat(market.marketHistory().getFirst().averagePrice()).isEqualTo(80_000L);
        assertThat(market.marketHistory().get(28).averagePrice()).isEqualTo(100_000L);
        assertThat(market.marketHistory().getLast().averagePrice()).isEqualTo(120_000L);
        assertThat(market.marketSummary().monthlyBidCount()).isEqualTo(5L);
        assertThat(market.marketSummary().dailyChangeRate()).isEqualByComparingTo("20.00");
        assertThat(market.marketSummary().weeklyChangeRate()).isEqualByComparingTo("50.00");
        assertThat(market.marketSummary().monthlyChangeRate()).isEqualByComparingTo("50.00");
    }

    private HomeAuctionRepository.DailyMarketAggregate daily(
            LocalDate date, Double price, Long bids) {
        var daily = mock(HomeAuctionRepository.DailyMarketAggregate.class);
        given(daily.getAuctionDate()).willReturn(date);
        given(daily.getAveragePrice()).willReturn(price);
        given(daily.getBidCount()).willReturn(bids);
        return daily;
    }
}
