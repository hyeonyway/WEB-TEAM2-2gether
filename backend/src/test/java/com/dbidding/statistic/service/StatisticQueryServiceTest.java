package com.dbidding.statistic.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dbidding.card.dto.CardResponses.StatisticCardSnapshot;
import com.dbidding.card.service.CardService;
import com.dbidding.statistic.repository.ItemDailyStatisticRepository;
import com.dbidding.statistic.repository.ItemStatisticRepository;
import com.dbidding.statistic.repository.PriceMovementCandidate;
import com.dbidding.statistic.domain.ItemDailyStatistic;
import com.dbidding.statistic.domain.ItemStatistic;
import com.dbidding.statistic.domain.MarketDailyStatistic;
import com.dbidding.statistic.repository.StatisticInsightQueryRepository;
import com.dbidding.statistic.repository.MarketDailyStatisticRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatisticQueryServiceTest {
    private final StatisticInsightQueryRepository insightQueryRepository =
            mock(StatisticInsightQueryRepository.class);
    private final ItemDailyStatisticRepository dailyStatisticRepository =
            mock(ItemDailyStatisticRepository.class);
    private final ItemStatisticRepository statisticRepository = mock(ItemStatisticRepository.class);
    private final MarketDailyStatisticRepository marketStatisticRepository =
            mock(MarketDailyStatisticRepository.class);
    private final CardService cardService = mock(CardService.class);
    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-28T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );
    private StatisticQueryService statisticQueryService;

    @BeforeEach
    void setUp() {
        statisticQueryService = new StatisticQueryService(
                insightQueryRepository, dailyStatisticRepository, statisticRepository, marketStatisticRepository,
                cardService, clock);
    }

    @Test
    void 진행_경매로_인사이트를_집계한다() {
        var aggregate = mock(StatisticInsightQueryRepository.InsightAggregate.class);
        given(aggregate.getTotalCount()).willReturn(11L);
        given(aggregate.getRisingCount()).willReturn(3L);
        given(aggregate.getAverageRisingRate()).willReturn(12.345);
        given(aggregate.getBidAuctionCount()).willReturn(7L);
        given(insightQueryRepository.aggregateInsights()).willReturn(aggregate);

        var insights = statisticQueryService.getInsights();

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
    void 오늘을_제외한_30일_통계에서_무거래일_가격은_null로_반환한다() {
        LocalDate from = LocalDate.of(2026, 6, 28);
        LocalDate to = LocalDate.of(2026, 7, 28);
        var dayBefore = daily(LocalDate.of(2026, 7, 26), 90_000L, 2, 180_000L);
        var yesterday = daily(LocalDate.of(2026, 7, 27), 100_000L, 3, 480_000L);
        given(marketStatisticRepository
                .findByStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(from, to))
                .willReturn(List.of(dayBefore, yesterday));
        var market = statisticQueryService.getMarket(30);

        verify(marketStatisticRepository)
                .findByStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(from, to);
        assertThat(market.marketHistory()).hasSize(30);
        assertThat(market.marketHistory().getFirst().averagePrice()).isNull();
        assertThat(market.marketHistory().getFirst().date()).isEqualTo("06/28");
        assertThat(market.marketHistory().getFirst().bidCount()).isZero();
        assertThat(market.marketHistory().get(28).averagePrice()).isEqualTo(90_000L);
        assertThat(market.marketHistory().getLast().averagePrice()).isEqualTo(100_000L);
        assertThat(market.marketHistory().getLast().date()).isEqualTo("07/27");
        assertThat(market.marketSummary().monthlyWinningPriceTotal()).isEqualTo(480_000L);
        assertThat(market.marketSummary().monthlyBidCount()).isEqualTo(5L);
        assertThat(market.marketSummary().monthlyEndedAuctionCount()).isEqualTo(5L);
        assertThat(market.marketSummary().monthlyHighestPrice()).isEqualTo(100_000L);
    }

    @Test
    void 가격_변동_TOP5는_오늘을_제외한_30일_범위의_최근_두_거래를_조회한다() {
        LocalDate from = LocalDate.of(2026, 6, 28);
        LocalDate today = LocalDate.of(2026, 7, 28);
        given(dailyStatisticRepository.findPriceMovementCandidates(from, today))
                .willReturn(List.of());
        given(cardService.getStatisticCardSnapshots(List.of())).willReturn(Map.of());

        var result = statisticQueryService.getPriceMovers(5);

        verify(dailyStatisticRepository).findPriceMovementCandidates(from, today);
        assertThat(result.gainers()).isEmpty();
        assertThat(result.losers()).isEmpty();
    }

    @Test
    void 가격_변동은_상승과_하락을_각각_정렬한다() {
        LocalDate from = LocalDate.of(2026, 6, 28);
        LocalDate today = LocalDate.of(2026, 7, 28);
        List<PriceMovementCandidate> candidates = List.of(
                candidate(1, 120_000L, 100_000L),
                candidate(2, 80_000L, 100_000L),
                candidate(3, 150_000L, 100_000L),
                candidate(4, 60_000L, 100_000L)
        );
        given(dailyStatisticRepository.findPriceMovementCandidates(from, today))
                .willReturn(candidates);
        given(cardService.getStatisticCardSnapshots(List.of(1, 2, 3, 4))).willReturn(Map.of(
                1, card(1), 2, card(2), 3, card(3), 4, card(4)));
        given(dailyStatisticRepository.findHistory(
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(today)
        )).willReturn(List.of());

        var result = statisticQueryService.getPriceMovers(5);

        assertThat(result.gainers()).extracting("cardId").containsExactly(3, 1);
        assertThat(result.losers()).extracting("cardId").containsExactly(4, 2);
    }

    @Test
    void 유효한_가격이_두개_미만인_후보는_가격_변동에서_제외한다() {
        LocalDate from = LocalDate.of(2026, 6, 28);
        LocalDate today = LocalDate.of(2026, 7, 28);
        var candidate = candidate(1, 120_000L, 0L);
        given(dailyStatisticRepository.findPriceMovementCandidates(from, today))
                .willReturn(List.of(candidate));
        var card = card(1);
        given(cardService.getStatisticCardSnapshots(List.of(1))).willReturn(Map.of(1, card));

        var result = statisticQueryService.getPriceMovers(5);

        assertThat(result.gainers()).isEmpty();
        assertThat(result.losers()).isEmpty();
        verify(dailyStatisticRepository, never()).findHistory(
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(today));
    }

    @Test
    void 카드_요약_통계를_조회_전용_DTO로_변환한다() {
        ItemStatistic statistic = mock(ItemStatistic.class);
        given(statistic.getItemId()).willReturn(10);
        given(statistic.getLatestPrice()).willReturn(138_000L);
        given(statistic.getAveragePrice30d()).willReturn(127_250L);
        given(statistic.getLowestPrice30d()).willReturn(105_000L);
        given(statistic.getHighestPrice30d()).willReturn(155_000L);
        given(statistic.getBidCount30d()).willReturn(32);
        given(statistic.getEndedAuctionCount30d()).willReturn(2);
        given(statistic.getWishlistCount()).willReturn(30);
        given(statistic.getDailyChangeRate()).willReturn(new BigDecimal("2.70"));
        given(statistic.getWeeklyChangeRate()).willReturn(new BigDecimal("8.20"));
        given(statistic.getMonthlyChangeRate()).willReturn(new BigDecimal("12.10"));
        given(statisticRepository.findById(10)).willReturn(Optional.of(statistic));

        var summary = statisticQueryService.getCardSummary(10).orElseThrow();

        assertThat(summary.cardId()).isEqualTo(10);
        assertThat(summary.latestPrice()).isEqualTo(138_000L);
        assertThat(summary.averagePrice30d()).isEqualTo(127_250L);
        assertThat(summary.bidCount30d()).isEqualTo(32);
        assertThat(summary.dailyChangeRate()).isEqualByComparingTo("2.70");
    }

    @Test
    void 카드_일간_통계를_조회_전용_DTO로_변환한다() {
        LocalDate date = LocalDate.of(2026, 7, 29);
        ItemDailyStatistic statistic = mock(ItemDailyStatistic.class);
        given(statistic.getStatisticsDate()).willReturn(date);
        given(statistic.getAveragePrice()).willReturn(null);
        given(statistic.getEndedAuctionCount()).willReturn(0);
        given(dailyStatisticRepository
                .findByItemIdAndStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
                        10, date, date.plusDays(1)))
                .willReturn(List.of(statistic));

        var dailyPrices = statisticQueryService.getDailyPrices(10, date, date.plusDays(1));

        assertThat(dailyPrices).singleElement().satisfies(daily -> {
            assertThat(daily.date()).isEqualTo(date);
            assertThat(daily.averagePrice()).isNull();
            assertThat(daily.endedAuctionCount()).isZero();
        });
    }

    private PriceMovementCandidate candidate(Integer id, Long current, Long previous) {
        var candidate = mock(PriceMovementCandidate.class);
        given(candidate.getCardId()).willReturn(id);
        given(candidate.getCurrentPrice()).willReturn(current);
        given(candidate.getPreviousPrice()).willReturn(previous);
        given(candidate.getCurrentDate()).willReturn(LocalDate.of(2026, 7, 27));
        given(candidate.getPreviousDate()).willReturn(LocalDate.of(2026, 7, 25));
        given(candidate.getBidCount()).willReturn(3);
        return candidate;
    }

    private StatisticCardSnapshot card(Integer id) {
        return new StatisticCardSnapshot(id, "카드 " + id, "gold", "/card-" + id);
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
