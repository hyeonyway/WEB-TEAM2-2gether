package com.dbidding.statistics.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.dbidding.card.domain.CardMetadata;
import com.dbidding.statistics.domain.ItemDailyStatistic;
import com.dbidding.statistics.domain.ItemStatistic;
import com.dbidding.statistics.repository.ItemDailyStatisticRepository;
import com.dbidding.statistics.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CardStatisticsAdapterTest {
    private final ItemStatisticRepository statisticRepository =
            mock(ItemStatisticRepository.class);
    private final ItemDailyStatisticRepository dailyStatisticRepository =
            mock(ItemDailyStatisticRepository.class);
    private final CardStatisticsAdapter adapter =
            new CardStatisticsAdapter(statisticRepository, dailyStatisticRepository);

    @Test
    void 통계_엔티티를_카드_도메인_요약_DTO로_변환한다() {
        CardMetadata card = mock(CardMetadata.class);
        ItemStatistic statistic = mock(ItemStatistic.class);
        given(card.getId()).willReturn(10);
        given(statistic.getItem()).willReturn(card);
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

        var summary = adapter.getSummary(10).orElseThrow();

        assertThat(summary.cardId()).isEqualTo(10);
        assertThat(summary.latestPrice()).isEqualTo(138_000L);
        assertThat(summary.averagePrice30d()).isEqualTo(127_250L);
        assertThat(summary.bidCount30d()).isEqualTo(32);
        assertThat(summary.dailyChangeRate()).isEqualByComparingTo("2.70");
    }

    @Test
    void 일간_통계_엔티티를_nullable_가격_DTO로_변환한다() {
        LocalDate date = LocalDate.of(2026, 7, 29);
        ItemDailyStatistic statistic = mock(ItemDailyStatistic.class);
        given(statistic.getStatisticsDate()).willReturn(date);
        given(statistic.getAveragePrice()).willReturn(null);
        given(statistic.getBidCount()).willReturn(0);
        given(dailyStatisticRepository
                .findByItemIdAndStatisticsDateGreaterThanEqualAndStatisticsDateLessThanOrderByStatisticsDate(
                        10, date, date.plusDays(1)))
                .willReturn(List.of(statistic));

        var dailyPrices = adapter.getDailyPrices(10, date, date.plusDays(1));

        assertThat(dailyPrices).singleElement().satisfies(daily -> {
            assertThat(daily.date()).isEqualTo(date);
            assertThat(daily.averagePrice()).isNull();
            assertThat(daily.bidCount()).isZero();
        });
    }
}
