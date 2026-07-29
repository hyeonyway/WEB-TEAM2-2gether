package com.dbidding.home.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dbidding.card.repository.ItemDailyStatisticRepository;
import com.dbidding.card.repository.PriceMovementCandidate;
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
    void 가격_변동_TOP5는_오늘을_제외한_30일_범위의_최근_두_거래를_조회한다() {
        LocalDate from = LocalDate.of(2026, 6, 28);
        LocalDate today = LocalDate.of(2026, 7, 28);
        given(dailyStatisticRepository.findPriceMovementCandidates(from, today))
                .willReturn(List.of());

        var result = homeService.getPriceMovers(5);

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
        given(dailyStatisticRepository.findHistory(
                org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.eq(from),
                org.mockito.ArgumentMatchers.eq(today)
        )).willReturn(List.of());

        var result = homeService.getPriceMovers(5);

        assertThat(result.gainers()).extracting("cardId").containsExactly(3, 1);
        assertThat(result.losers()).extracting("cardId").containsExactly(4, 2);
    }

    private PriceMovementCandidate candidate(Integer id, Long current, Long previous) {
        var candidate = mock(PriceMovementCandidate.class);
        given(candidate.getCardId()).willReturn(id);
        given(candidate.getName()).willReturn("카드 " + id);
        given(candidate.getCurrentPrice()).willReturn(current);
        given(candidate.getPreviousPrice()).willReturn(previous);
        given(candidate.getCurrentDate()).willReturn(LocalDate.of(2026, 7, 27));
        given(candidate.getPreviousDate()).willReturn(LocalDate.of(2026, 7, 25));
        given(candidate.getBidCount()).willReturn(3);
        return candidate;
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
