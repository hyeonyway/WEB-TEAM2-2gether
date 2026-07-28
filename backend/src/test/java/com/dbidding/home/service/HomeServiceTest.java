package com.dbidding.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.card.repository.ItemDailyStatisticRepository;
import com.dbidding.home.domain.MarketDailyStatistic;
import com.dbidding.home.repository.HomeAuctionRepository;
import com.dbidding.home.repository.MarketDailyStatisticRepository;
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
    private final ItemDailyStatisticRepository dailyStatisticRepository =
            mock(ItemDailyStatisticRepository.class);
    private final MarketDailyStatisticRepository marketStatisticRepository =
            mock(MarketDailyStatisticRepository.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private HomeService homeService;

    @BeforeEach
    void setUp() {
        homeService = new HomeService(
                auctionRepository, dailyStatisticRepository, marketStatisticRepository, clock);
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
    void 오늘을_제외한_종료_경매를_30일간_집계하고_무거래일에는_가격을_이월한다() {
        LocalDate from = LocalDate.of(2026, 6, 28);
        LocalDate to = LocalDate.of(2026, 7, 28);
        var dayBefore = daily(LocalDate.of(2026, 7, 26), 90_000L, 2, 180_000L);
        var yesterday = daily(LocalDate.of(2026, 7, 27), 100_000L, 3, 480_000L);
        given(marketStatisticRepository
                .findByStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(from, to))
                .willReturn(List.of(dayBefore, yesterday));
        var previous = daily(LocalDate.of(2026, 6, 27), 80_000L, 1, 80_000L);
        given(marketStatisticRepository
                .findFirstByStatisticsDateLessThanOrderByStatisticsDateDesc(from))
                .willReturn(java.util.Optional.of(previous));

        var market = homeService.getMarket(30);

        verify(marketStatisticRepository)
                .findByStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(from, to);
        assertThat(market.marketHistory()).hasSize(30);
        assertThat(market.marketHistory().getFirst().averagePrice()).isEqualTo(80_000L);
        assertThat(market.marketHistory().getFirst().date()).isEqualTo("06/28");
        assertThat(market.marketHistory().get(28).averagePrice()).isEqualTo(90_000L);
        assertThat(market.marketHistory().getLast().averagePrice()).isEqualTo(100_000L);
        assertThat(market.marketHistory().getLast().date()).isEqualTo("07/27");
        assertThat(market.marketSummary().monthlyWinningPriceTotal()).isEqualTo(480_000L);
        assertThat(market.marketSummary().monthlyBidCount()).isEqualTo(5L);
        assertThat(market.marketSummary().monthlyEndedAuctionCount()).isEqualTo(5L);
        assertThat(market.marketSummary().monthlyHighestPrice()).isEqualTo(100_000L);
    }

    @Test
    void 상승_TOP5는_오늘_자정_이전의_어제와_그제_통계만_조회한다() {
        LocalDate yesterday = LocalDate.of(2026, 7, 27);
        LocalDate dayBefore = LocalDate.of(2026, 7, 26);
        given(dailyStatisticRepository.findAllWithItemByStatisticsDate(yesterday)).willReturn(List.of());
        given(dailyStatisticRepository.findAllWithItemByStatisticsDate(dayBefore)).willReturn(List.of());

        var result = homeService.getTopGainers(5);

        verify(dailyStatisticRepository).findAllWithItemByStatisticsDate(yesterday);
        verify(dailyStatisticRepository).findAllWithItemByStatisticsDate(dayBefore);
        assertThat(result.topGainers()).isEmpty();
    }

    private MarketDailyStatistic daily(
            LocalDate date, Long price, Integer bids, Long winningPriceTotal) {
        var daily = mock(MarketDailyStatistic.class);
        given(daily.getStatisticsDate()).willReturn(date);
        given(daily.getAveragePrice()).willReturn(price);
        given(daily.getBidCount()).willReturn(bids);
        given(daily.getWinningPriceTotal30d()).willReturn(winningPriceTotal);
        given(daily.getBidCount30d()).willReturn(5);
        given(daily.getEndedAuctionCount30d()).willReturn(5);
        given(daily.getHighestPrice30d()).willReturn(price);
        return daily;
    }
}
