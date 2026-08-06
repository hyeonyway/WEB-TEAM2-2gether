package com.dbidding.order;

import com.dbidding.auction.event.AuctionClosedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * {@code orders.auction_id}의 유니크 제약(uk_orders_auction) 덕분에, 이 리스너가 같은
 * {@link AuctionClosedEvent}에 대해 중복 실행되더라도 두 번째 저장 시도는 제약 위반으로
 * 걸러진다 — notification의 중복 처리 패턴과 동일하다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final OrderRepository orderRepository;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        if (event.winnerId() == null) {
            return;
        }
        try {
            orderRepository.save(Order.pendingConfirm(
                    event.auctionId(), event.winnerId(), event.sellerId(), event.winningPrice()
            ));
        } catch (DataIntegrityViolationException exception) {
            log.debug("event=order.creation.duplicate_skipped auctionId={}", event.auctionId(), exception);
        }
    }
}
