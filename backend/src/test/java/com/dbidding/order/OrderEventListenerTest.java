package com.dbidding.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.event.AuctionClosedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTest {

    private static final Integer AUCTION_ID = 10;
    private static final Integer ITEM_ID = 1;
    private static final Integer WINNER_ID = 1;
    private static final Integer SELLER_ID = 2;

    @Mock
    private OrderRepository orderRepository;

    private OrderEventListener orderEventListener;

    private AuctionClosedEvent closedWithWinner() {
        return new AuctionClosedEvent(
                AUCTION_ID, ITEM_ID, "리자몽", "PSA10", "KR", "http://image",
                WINNER_ID, SELLER_ID, 10_000L, 50_000L, 50_000L, 1_000L, 5,
                LocalDateTime.now(), AuctionStatus.ENDED, 1L, LocalDateTime.now()
        );
    }

    private AuctionClosedEvent closedWithoutWinner() {
        return new AuctionClosedEvent(
                AUCTION_ID, ITEM_ID, "리자몽", "PSA10", "KR", "http://image",
                null, SELLER_ID, 10_000L, 10_000L, null, 1_000L, 0,
                LocalDateTime.now(), AuctionStatus.FAILED, 1L, LocalDateTime.now()
        );
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        orderEventListener = new OrderEventListener(orderRepository);
    }

    @Test
    void 낙찰자가_있으면_주문을_생성한다() {
        orderEventListener.handleAuctionClosed(closedWithWinner());

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void 낙찰자가_없으면_주문을_생성하지_않는다() {
        orderEventListener.handleAuctionClosed(closedWithoutWinner());

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 중복_이벤트로_유니크_제약_위반이_나도_예외를_전파하지_않는다() {
        given(orderRepository.save(any(Order.class))).willThrow(new DataIntegrityViolationException("duplicate"));

        orderEventListener.handleAuctionClosed(closedWithWinner());

        verify(orderRepository).save(any(Order.class));
    }
}
