package com.dbidding.order;

import com.dbidding.auction.event.AuctionClosedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Spring 이벤트 구독 방식에 대한 얇은 어댑터일 뿐이다 — 실제 생성 로직은
 * {@link OrderService#createFromAuctionClosed}에 있다. 구독 방식이 바뀌어도
 * (예: 다른 전송 수단으로 교체) 이 클래스만 다시 쓰면 된다.
 */
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderService orderService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        orderService.createFromAuctionClosed(event);
    }
}
