package com.dbidding.card.service;

import com.dbidding.card.repository.CardMetadataRepository;
import com.dbidding.card.repository.ItemStatisticRepository;
import java.time.LocalDateTime;
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

        verify(statisticRepository, never()).refreshActiveAuctionCount(anyInt());
    }

    @Test
    void 경매가_종료되면_낙찰_통계와_변동률을_갱신한다() {
        LocalDateTime date = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(cardRepository.existsById(1)).thenReturn(true);

        service.recordAuctionCompleted(1, 110_000L, date);

        verify(statisticRepository).refreshActiveAuctionCount(1);
    }

    @Test
    void 낙찰가는_0보다_커야_한다() {
        when(cardRepository.existsById(1)).thenReturn(true);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.recordAuctionCompleted(1, 0, LocalDateTime.now())))
                .isInstanceOf(IllegalArgumentException.class);
        verify(statisticRepository, never()).refreshActiveAuctionCount(anyInt());
    }
}
