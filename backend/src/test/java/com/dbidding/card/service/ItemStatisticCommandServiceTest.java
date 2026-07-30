package com.dbidding.card.service;

import com.dbidding.statistics.service.ItemStatisticCommandService;
import com.dbidding.statistics.port.StatisticsCardPort;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ItemStatisticCommandServiceTest {
    private final StatisticsCardPort cardPort = mock(StatisticsCardPort.class);
    private final ItemStatisticCommandService service =
            new ItemStatisticCommandService(cardPort);

    @Test
    void 입찰이_발생하면_해당_날짜의_입찰_수를_증가시킨다() {
        LocalDateTime date = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(cardPort.exists(1)).thenReturn(true);

        service.recordBid(1, date);

        verify(cardPort).exists(1);
    }

    @Test
    void 경매가_종료되면_카드만_검증하고_활성_경매_수를_저장하지_않는다() {
        LocalDateTime date = LocalDateTime.of(2026, 7, 27, 12, 0);
        when(cardPort.exists(1)).thenReturn(true);

        service.recordAuctionCompleted(1, 110_000L, date);

        verify(cardPort).exists(1);
    }

    @Test
    void 낙찰가는_0보다_커야_한다() {
        when(cardPort.exists(1)).thenReturn(true);

        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> service.recordAuctionCompleted(1, 0, LocalDateTime.now())))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
