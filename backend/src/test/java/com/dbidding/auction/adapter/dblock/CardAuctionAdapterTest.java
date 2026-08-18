package com.dbidding.auction.adapter.dblock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CardAuctionAdapterTest {
    @Mock
    private AuctionRepository auctionRepository;

    private final Clock clock = Clock.fixed(
            Instant.parse("2026-07-31T03:00:00Z"),
            ZoneId.of("Asia/Seoul")
    );

    @Test
    void 종료_시각이_지나지_않은_OPEN과_ENDING_경매만_진행_경매로_집계한다() {
        Instant now = clock.instant();
        CardAuctionAdapter adapter = new CardAuctionAdapter(auctionRepository, clock);
        when(auctionRepository.countByItemIdAndStatusInAndCloseTimeAfter(
                47,
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                now
        )).thenReturn(0L);

        int activeAuctionCount = adapter.countActiveAuctions(47);

        assertThat(activeAuctionCount).isZero();
        verify(auctionRepository).countByItemIdAndStatusInAndCloseTimeAfter(
                47,
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING),
                now
        );
    }
}
