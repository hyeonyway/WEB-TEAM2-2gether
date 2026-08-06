package com.dbidding.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.order.exception.InvalidOrderStatusException;
import com.dbidding.order.exception.OrderAccessDeniedException;
import com.dbidding.order.exception.OrderNotFoundException;
import com.dbidding.order.port.OrderEventPort;
import com.dbidding.order.port.WalletSettlementPort;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Integer BUYER_ID = 1;
    private static final Integer SELLER_ID = 2;
    private static final Integer ORDER_ID = 100;
    private static final Integer AUCTION_ID = 10;
    private static final String CARD_NAME = "리자몽";
    private static final long PRICE = 50_000L;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private WalletSettlementPort walletSettlementPort;

    @Mock
    private OrderEventPort orderEventPort;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, walletSettlementPort, orderEventPort);
    }

    private Order pendingOrder() {
        return Order.pendingConfirm(AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME, PRICE);
    }

    @Test
    void 구매자가_구매확정하면_판매자에게_정산하고_완료_이벤트를_발행한다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        Order result = orderService.confirm(ORDER_ID, BUYER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        verify(walletSettlementPort).payoutToSeller(SELLER_ID, order.getId(), PRICE);
        verify(orderEventPort).publishCompleted(
                new OrderCompletedEvent(order.getId(), AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME)
        );
    }

    @Test
    void 구매자가_구매취소하면_구매자에게_환불하고_취소_이벤트를_발행한다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        Order result = orderService.cancel(ORDER_ID, BUYER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(walletSettlementPort).refundToBuyer(BUYER_ID, order.getId(), PRICE);
        verify(orderEventPort).publishCancelled(
                new OrderCancelledEvent(order.getId(), AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME, OrderCancelledEvent.CancelledBy.BUYER)
        );
    }

    @Test
    void 판매자가_판매취소하면_구매자에게_환불하고_판매자_취소로_이벤트를_발행한다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        Order result = orderService.sellerCancel(ORDER_ID, SELLER_ID);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(walletSettlementPort).refundToBuyer(BUYER_ID, order.getId(), PRICE);
        verify(orderEventPort).publishCancelled(
                new OrderCancelledEvent(order.getId(), AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME, OrderCancelledEvent.CancelledBy.SELLER)
        );
    }

    @Test
    void 판매자가_아니면_판매취소_시도시_예외가_발생하고_환불은_호출되지_않는다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.sellerCancel(ORDER_ID, BUYER_ID))
                .isInstanceOf(OrderAccessDeniedException.class);
        verify(walletSettlementPort, never()).refundToBuyer(any(), any(), anyLong());
    }

    @Test
    void 이미_확정된_주문을_판매취소하면_예외가_발생한다() {
        Order order = pendingOrder();
        order.confirm();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.sellerCancel(ORDER_ID, SELLER_ID))
                .isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void 존재하지_않는_주문을_확정하면_예외가_발생한다() {
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.confirm(ORDER_ID, BUYER_ID))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void 본인_소유가_아닌_주문을_확정하면_예외가_발생하고_정산은_호출되지_않는다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(ORDER_ID, 999))
                .isInstanceOf(OrderAccessDeniedException.class);
        verify(walletSettlementPort, never()).payoutToSeller(any(), any(), anyLong());
    }

    @Test
    void 이미_확정된_주문을_다시_확정하면_예외가_발생한다() {
        Order order = pendingOrder();
        order.confirm();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(ORDER_ID, BUYER_ID))
                .isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void 이미_취소된_주문을_확정하면_예외가_발생한다() {
        Order order = pendingOrder();
        order.cancel();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.confirm(ORDER_ID, BUYER_ID))
                .isInstanceOf(InvalidOrderStatusException.class);
    }

    @Test
    void 구매자나_판매자가_아니면_주문_조회시_예외가_발생한다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.findOne(ORDER_ID, 999))
                .isInstanceOf(OrderAccessDeniedException.class);
    }

    @Test
    void 판매자는_본인이_판매한_주문을_조회할_수_있다() {
        Order order = pendingOrder();
        given(orderRepository.findById(ORDER_ID)).willReturn(Optional.of(order));

        Order result = orderService.findOne(ORDER_ID, SELLER_ID);

        assertThat(result).isEqualTo(order);
    }

    @Test
    void 낙찰자가_있으면_주문을_생성한다() {
        orderService.createFromAuctionClosed(AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME, PRICE);

        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void 낙찰자가_없으면_주문을_생성하지_않는다() {
        orderService.createFromAuctionClosed(AUCTION_ID, null, SELLER_ID, CARD_NAME, PRICE);

        verify(orderRepository, never()).save(any());
    }

    @Test
    void 중복_호출로_유니크_제약_위반이_나도_예외를_전파하지_않는다() {
        given(orderRepository.save(any(Order.class))).willThrow(new DataIntegrityViolationException("duplicate"));

        orderService.createFromAuctionClosed(AUCTION_ID, BUYER_ID, SELLER_ID, CARD_NAME, PRICE);

        verify(orderRepository).save(any(Order.class));
    }
}
