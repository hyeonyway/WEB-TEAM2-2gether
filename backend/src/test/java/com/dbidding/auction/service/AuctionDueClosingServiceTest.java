package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.repository.AuctionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AuctionDueClosingServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Mock
    private AuctionRepository auctionRepository;
    @Mock
    private AuctionCommandService auctionCommandService;

    private AuctionDueClosingService service;

    @BeforeEach
    void setUp() {
        service = new AuctionDueClosingService(auctionRepository, auctionCommandService, Runnable::run);
    }

    @Test
    void 락_없는_마감_대상_ID를_조회한_뒤_각_경매를_개별_종료_트랜잭션으로_처리한다() {
        AuctionCloseResponse first = response(1);
        AuctionCloseResponse second = response(2);
        when(auctionRepository.findDueAuctionIds(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), NOW, PageRequest.of(0, 100)
        )).thenReturn(List.of(1, 2));
        when(auctionCommandService.closeDueAuction(1, NOW)).thenReturn(Optional.of(first));
        when(auctionCommandService.closeDueAuction(2, NOW)).thenReturn(Optional.of(second));

        List<AuctionCloseResponse> responses = service.closeDueAuctions(NOW, 100);

        assertThat(responses).containsExactly(first, second);
        verify(auctionRepository).findDueAuctionIds(
                List.of(AuctionStatus.OPEN, AuctionStatus.ENDING), NOW, PageRequest.of(0, 100)
        );
        verify(auctionCommandService).closeDueAuction(1, NOW);
        verify(auctionCommandService).closeDueAuction(2, NOW);
    }

    private AuctionCloseResponse response(Integer auctionId) {
        return new AuctionCloseResponse(auctionId, AuctionStatus.ENDED, 2, 1L, 30_000L, NOW);
    }
}
