package com.dbidding.notification;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.notification.port.CardNameFinder;
import com.dbidding.notification.port.WishlistUserFinder;
import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * notification의 (user_id, auction_id, type, bid_id) 유니크 제약 덕분에, 복구 배치가 이
 * 이벤트보다 먼저 같은 알림을 저장해뒀을 수 있다 — 그 경우 여기서의 insert는 정상적으로
 * 중복 위반이 난다. 배치는 저장만 하고 SSE push는 안 하므로, 이 경우에도 저장은 스킵하되
 * push는 그대로 해야 지금 연결돼 있는 유저가 실시간 알림을 놓치지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final WishlistUserFinder wishlistUserFinder;
    private final CardNameFinder cardNameFinder;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final NotificationSseConnectionManager notificationSseConnectionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionOpened(AuctionOpenedEvent event) {
        String message = event.cardName() + " 카드의 경매가 등록되었습니다.";
        List<Integer> userIds = wishlistUserFinder.findUserIdsByCardId(event.itemId());
        if (userIds.isEmpty()) {
            return;
        }
        notificationService.saveAllIgnoringDuplicates(userIds, event.auctionId(), NotificationType.AUCTION_OPENED, message)
                .forEach(notification ->
                        notificationSseConnectionManager.push(notification.getUserId(), NotificationResponse.from(notification)));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBidPlaced(BidPlacedEvent event) {
        if (event.previousBidderId() == null) {
            return;
        }
        String cardName = cardNameFinder.findNameById(event.itemId());
        String message = cardName + " 카드 경매에 " + "%,d".formatted(event.currentPrice()) + "원에 상회 입찰이 발생했습니다.";
        saveAndPush(event.previousBidderId(), event.auctionId(), NotificationType.OUTBID, event.previousBidId(), message);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        boolean won = event.winnerId() != null;
        NotificationType type = won ? NotificationType.AUCTION_WON : NotificationType.AUCTION_UNSOLD;
        if (won) {
            String winnerMessage = event.cardName() + " 카드 경매에 낙찰되었습니다.";
            saveAndPush(event.winnerId(), event.auctionId(), type, Notification.NO_BID, winnerMessage);
        }
        String sellerMessage = event.cardName() + " 카드 경매가 " + (won ? "낙찰되었습니다." : "유찰되었습니다.");
        saveAndPush(event.sellerId(), event.auctionId(), type, Notification.NO_BID, sellerMessage);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        saveAndPush(event.buyerId(), event.auctionId(), NotificationType.ORDER_COMPLETED, Notification.NO_BID,
                event.cardName() + " 카드 구매가 확정되었습니다.");
        saveAndPush(event.sellerId(), event.auctionId(), NotificationType.ORDER_COMPLETED, Notification.NO_BID,
                event.cardName() + " 카드 판매 대금이 정산되었습니다.");
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        boolean cancelledBySeller = event.cancelledBy() == OrderCancelledEvent.CancelledBy.SELLER;
        String buyerMessage = cancelledBySeller
                ? "판매자가 " + event.cardName() + " 카드 거래를 취소하여 환불되었습니다."
                : event.cardName() + " 카드 구매가 취소되어 환불되었습니다.";
        String sellerMessage = cancelledBySeller
                ? event.cardName() + " 카드 판매를 취소했습니다."
                : "구매자가 " + event.cardName() + " 카드 거래를 취소했습니다.";
        saveAndPush(event.buyerId(), event.auctionId(), NotificationType.ORDER_CANCELLED, Notification.NO_BID, buyerMessage);
        saveAndPush(event.sellerId(), event.auctionId(), NotificationType.ORDER_CANCELLED, Notification.NO_BID, sellerMessage);
    }

    private void saveAndPush(Integer userId, Integer auctionId, NotificationType type, Long bidId, String message) {
        Notification notification;
        try {
            notification = Notification.NO_BID.equals(bidId)
                    ? notificationService.save(userId, auctionId, type, message)
                    : notificationService.saveForBid(userId, auctionId, type, bidId, message);
        } catch (DataIntegrityViolationException exception) {
            log.debug("event=notification.live.duplicate_skipped type={} auctionId={} bidId={}", type, auctionId, bidId, exception);
            notification = notificationRepository.findByUserIdAndAuctionIdAndTypeAndBidId(userId, auctionId, type, bidId)
                    .orElseThrow(() -> exception);
        }
        notificationSseConnectionManager.push(userId, NotificationResponse.from(notification));
    }
}
