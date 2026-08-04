package com.dbidding.auction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.dto.AuctionCloseResponse;
import com.dbidding.auction.dto.BidCreateRequest;
import com.dbidding.auction.dto.BidResponses;
import com.dbidding.auction.metrics.AuctionMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AuctionServiceMetricsTest {

    @Mock
    private AuctionCommandService commandService;
    @Mock
    private AuctionQueryService queryService;

    private SimpleMeterRegistry registry;
    private AuctionService service;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        service = new AuctionService(commandService, queryService, new AuctionMetrics(registry));
    }

    @Test
    void 커밋까지_완료된_입찰을_accepted로_기록한다() {
        BidCreateRequest request = new BidCreateRequest(43_000L);
        when(commandService.participate(2, 1, request, "bid-key"))
                .thenReturn(mock(BidResponses.BidResult.class));

        service.participate(2, 1, request, "bid-key");

        assertThat(timerCount("dbidding.bid.duration", "result", "accepted")).isEqualTo(1);
        assertThat(timerCount("dbidding.bid.duration", "result", "rejected")).isZero();
    }

    @Test
    void 유효성_위반_입찰을_rejected로_기록한다() {
        BidCreateRequest request = new BidCreateRequest(42_000L);
        when(commandService.participate(2, 1, request, "bid-key"))
                .thenThrow(new ResponseStatusException(BAD_REQUEST, "최소 입찰가 미만"));

        assertThatThrownBy(() -> service.participate(2, 1, request, "bid-key"))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(timerCount("dbidding.bid.duration", "result", "rejected")).isEqualTo(1);
    }

    @Test
    void 낙찰된_경매_종료를_with_winner로_기록한다() {
        when(commandService.closeAuction(1)).thenReturn(new AuctionCloseResponse(
                1,
                AuctionStatus.ENDED,
                2,
                10L,
                43_000L,
                LocalDateTime.of(2026, 8, 4, 12, 0),
                2L
        ));

        service.closeAuction(1);

        assertThat(timerCount("dbidding.auction.close.duration", "result", "with_winner"))
                .isEqualTo(1);
    }

    private double timerCount(String name, String tagName, String tagValue) {
        return registry.get(name).tag(tagName, tagValue).timer().count();
    }
}
