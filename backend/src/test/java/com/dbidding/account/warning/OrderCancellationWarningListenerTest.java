package com.dbidding.account.warning;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.dbidding.order.event.OrderCancelledEvent;

class OrderCancellationWarningListenerTest {

	@Test
	void 구매자_취소는_구매자에게_구매자_취소_경고를_발급한다() {
		OrderCancellationWarningService service = org.mockito.Mockito.mock(OrderCancellationWarningService.class);
		OrderCancellationWarningListener listener = new OrderCancellationWarningListener(service);

		listener.handle(new OrderCancelledEvent(10, 20, 30, 40, "card", OrderCancelledEvent.CancelledBy.BUYER));

		verify(service).issue(30, 10, UserWarningReason.BUYER_CANCELLED);
	}

	@Test
	void 판매자_취소는_판매자에게_판매자_취소_경고를_발급한다() {
		OrderCancellationWarningService service = org.mockito.Mockito.mock(OrderCancellationWarningService.class);
		OrderCancellationWarningListener listener = new OrderCancellationWarningListener(service);

		listener.handle(new OrderCancelledEvent(10, 20, 30, 40, "card", OrderCancelledEvent.CancelledBy.SELLER));

		verify(service).issue(40, 10, UserWarningReason.SELLER_CANCELLED);
	}

	@Test
	void 주문_취소_경고는_커밋_이후에_트랜잭션_밖_이벤트도_처리한다() throws Exception {
		TransactionalEventListener listener = OrderCancellationWarningListener.class
			.getMethod("handle", OrderCancelledEvent.class)
			.getAnnotation(TransactionalEventListener.class);

		org.assertj.core.api.Assertions.assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
		org.assertj.core.api.Assertions.assertThat(listener.fallbackExecution()).isTrue();
	}
}
