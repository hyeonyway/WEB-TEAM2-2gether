package com.dbidding.order;

import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    @Mock
    private OrderService orderService;

    private OrderEventListener orderEventListener;

    @BeforeEach
    void setUp() {
        orderEventListener = new OrderEventListener(orderService);
    }

    @Test
    void 경매_종료_이벤트를_받으면_OrderService에_그대로_위임한다() {
        AuctionClosedEvent event = new AuctionClosedEvent(
                10, 1, "리자몽", "PSA10", "KR", "http://image",
                1, 2, 10_000L, 50_000L, 50_000L, 1_000L, 5,
                LocalDateTime.now(), AuctionStatus.ENDED, 1L, LocalDateTime.now()
        );

        orderEventListener.handleAuctionClosed(event);

        verify(orderService).createFromAuctionClosed(event);
    }
}
