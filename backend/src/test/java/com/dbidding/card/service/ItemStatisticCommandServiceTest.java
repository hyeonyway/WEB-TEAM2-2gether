package com.dbidding.card.service;

import com.dbidding.card.domain.ItemStatistic;
import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
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
        LocalDateTime date = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(cardRepository.existsById(1)).thenReturn(true);

        service.recordBid(1, date);

        verify(statisticRepository).incrementBidCount(1, date);
    }

    @Test
    void 경매가_종료되면_낙찰_통계와_변동률을_갱신한다() {
        LocalDateTime date = LocalDateTime.of(2026, 7, 27, 12, 0);
        ItemStatistic current = statistic(date, 110_000L);
        ItemStatistic previous = statistic(date.minusDays(1), 100_000L);
        when(cardRepository.existsById(1)).thenReturn(true);
        when(statisticRepository.findByItemIdAndStatisticsDate(1, date))
                .thenReturn(Optional.of(current));
        when(statisticRepository
                .findFirstByItemIdAndStatisticsDateLessThanEqualOrderByStatisticsDateDesc(
                        eq(1), any(LocalDateTime.class)))
                .thenReturn(Optional.of(previous));

        service.recordAuctionCompleted(1, 110_000L, date);

        verify(statisticRepository).recordCompletedAuction(1, date, 110_000L);
        verify(statisticRepository).refreshActiveAuctionCount(1, date);
        assertThat(current.getDailyChangeRate()).isEqualByComparingTo("10.00");
        assertThat(current.getWeeklyChangeRate()).isEqualByComparingTo("10.00");
        assertThat(current.getMonthlyChangeRate()).isEqualByComparingTo("10.00");
    }

    @Test
    void 낙찰가는_0보다_커야_한다() {
        when(cardRepository.existsById(1)).thenReturn(true);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.recordAuctionCompleted(1, 0, LocalDateTime.now())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(statisticRepository, never())
                .recordCompletedAuction(anyInt(), any(LocalDateTime.class), anyLong());
    }

    private ItemStatistic statistic(LocalDateTime date, long price) {
        return new ItemStatistic(
                null, date, price, price, price, price,
                1, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO
        );
    }
}
