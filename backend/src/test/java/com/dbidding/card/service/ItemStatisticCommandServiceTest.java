package com.dbidding.card.service;

import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ItemStatisticCommandServiceTest {
    private final CardMetadataRepository cardRepository = mock(CardMetadataRepository.class);
    private final ItemStatisticRepository statisticRepository = mock(ItemStatisticRepository.class);
    private final ItemStatisticCommandService service =
            new ItemStatisticCommandService(cardRepository, statisticRepository);

    @Test
    void 입찰이_발생하면_해당_날짜의_입찰_수를_증가시킨다() {
        LocalDate date = LocalDate.of(2026, 7, 27);
        when(cardRepository.existsById(1L)).thenReturn(true);

        service.recordBid(1L, date);

        verify(statisticRepository).incrementBidCount(1L, date);
    }

    @Test
    void 경매가_종료되면_낙찰_통계와_변동률을_갱신한다() {
        LocalDate date = LocalDate.of(2026, 7, 27);
        ItemStatistic current = statistic(date, 110_000L);
        ItemStatistic previous = statistic(date.minusDays(1), 100_000L);
        when(cardRepository.existsById(1L)).thenReturn(true);
        when(statisticRepository.findByItemIdAndStatisticsDate(1L, date))
                .thenReturn(Optional.of(current));
        when(statisticRepository
                .findFirstByItemIdAndStatisticsDateLessThanEqualOrderByStatisticsDateDesc(
                        eq(1L), any(LocalDate.class)))
                .thenReturn(Optional.of(previous));

        service.recordAuctionCompleted(1L, 110_000L, date);

        verify(statisticRepository).recordCompletedAuction(1L, date, 110_000L);
        verify(statisticRepository).refreshActiveAuctionCount(1L, date);
        assertThat(current.getDailyChangeRate()).isEqualByComparingTo("10.00");
        assertThat(current.getWeeklyChangeRate()).isEqualByComparingTo("10.00");
        assertThat(current.getMonthlyChangeRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void 낙찰가는_0보다_커야_한다() {
        when(cardRepository.existsById(1L)).thenReturn(true);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.recordAuctionCompleted(1L, 0, LocalDate.now())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(statisticRepository, never())
                .recordCompletedAuction(anyLong(), any(LocalDate.class), anyLong());
    }

    private ItemStatistic statistic(LocalDate date, long price) {
        return new ItemStatistic(
                null, date, price, price, price, price,
                1, 1, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}
