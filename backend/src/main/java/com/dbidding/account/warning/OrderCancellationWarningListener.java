package com.dbidding.account.warning;

import com.dbidding.order.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCancellationWarningListener {

	private final OrderCancellationWarningService warningService;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	public void handle(OrderCancelledEvent event) {
		if (event.cancelledBy() == OrderCancelledEvent.CancelledBy.BUYER) {
			warningService.issue(event.buyerId(), event.orderId(), UserWarningReason.BUYER_CANCELLED);
			return;
		}
		warningService.issue(event.sellerId(), event.orderId(), UserWarningReason.SELLER_CANCELLED);
	}
}
