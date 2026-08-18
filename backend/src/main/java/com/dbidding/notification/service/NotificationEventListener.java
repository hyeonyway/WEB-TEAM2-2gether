package com.dbidding.notification.service;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.card.service.CardPriceService;
import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationInsertRow;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.notification.repository.NotificationRepository;
import com.dbidding.notification.sse.NotificationPushMessage;
import com.dbidding.notification.sse.NotificationPushPublisher;
import com.dbidding.order.event.OrderCancelledEvent;
import com.dbidding.order.event.OrderCompletedEvent;
import com.dbidding.wishlist.service.WishlistService;
import java.time.Instant;
import java.util.ArrayList;
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
 * 이벤트보다 먼저 같은 알림을 저장해뒀을 수 있다. 배치는 저장만 하고 SSE push는 안 하므로,
 * 이 경우에도 저장은 스킵하되 push는 그대로 해야 지금 연결돼 있는 유저가 실시간 알림을
 * 놓치지 않는다. 단건 경로({@link #saveAndPush}, JPA INSERT)는 이 중복이 예외로 나서
 * catch 후 재조회하고, 다건 경로({@link #saveAllAndPush}, {@code INSERT IGNORE})는 애초에
 * 예외 없이 조용히 건너뛴다 — 어느 쪽이든 push는 항상 나간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final WishlistService wishlistService;
    private final CardPriceService cardPriceService;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final NotificationPushPublisher notificationPushPublisher;

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionOpened(AuctionOpenedEvent event) {
        String message = event.cardName() + " 카드의 경매가 등록되었습니다.";
        List<Integer> userIds = wishlistService.findUserIdsByCardId(event.itemId());
        if (userIds.isEmpty()) {
            return;
        }
        notificationService.saveAllIgnoringDuplicates(userIds, event.auctionId(), NotificationType.AUCTION_OPENED, message);

        // 전원이 완전히 동일한 내용(type/auctionId/message)을 받으므로 유저마다 새로 만들지 않고
        // 하나만 만들어 재사용한다. 저장 후 재조회를 안 하므로 id/createdAt은 근사값(placeholder
        // 0L/현재 시각)이고, 다음 GET /notifications 조회 때 실제 값으로 교정된다.
        NotificationResponse sharedPayload = new NotificationResponse(
                0L, event.auctionId(), NotificationType.AUCTION_OPENED, Notification.NO_BID, message, false, Instant.now());
        List<NotificationPushMessage> pushMessages = userIds.stream()
                .map(userId -> new NotificationPushMessage(userId, sharedPayload))
                .toList();
        notificationPushPublisher.publish(pushMessages);
    }

    // fallbackExecution=true: #281 이후 이 이벤트가 트랜잭션 밖(AuctionCommandService, 이미
    // 커밋된 뒤)에서도 발행되므로, 없으면 활성 트랜잭션이 없을 때 조용히 드랍된다.
    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleBidPlaced(BidPlacedEvent event) {
        if (event.previousBidderId() == null) {
            return;
        }
        String cardName = cardPriceService.getCard(event.itemId(), 1).name();
        String message = cardName + " 카드 경매에 " + "%,d".formatted(event.currentPrice()) + "원에 상회 입찰이 발생했습니다.";
        saveAndPush(event.previousBidderId(), event.auctionId(), NotificationType.OUTBID, event.previousBidId(), message);
    }

    // fallbackExecution=true: 위 handleBidPlaced와 동일한 이유(즉시낙찰 시 트랜잭션 밖에서 발행됨).
    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        boolean won = event.winnerId() != null;
        NotificationType type = won ? NotificationType.AUCTION_WON : NotificationType.AUCTION_UNSOLD;
        List<Recipient> recipients = new ArrayList<>();
        if (won) {
            recipients.add(new Recipient(event.winnerId(), event.cardName() + " 카드 경매에 낙찰되었습니다."));
        }
        recipients.add(new Recipient(event.sellerId(), event.cardName() + " 카드 경매가 " + (won ? "낙찰되었습니다." : "유찰되었습니다.")));
        saveAllAndPush(event.auctionId(), type, recipients);
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleOrderCompleted(OrderCompletedEvent event) {
        saveAllAndPush(event.auctionId(), NotificationType.ORDER_COMPLETED, List.of(
                new Recipient(event.buyerId(), event.cardName() + " 카드 구매가 확정되었습니다."),
                new Recipient(event.sellerId(), event.cardName() + " 카드 판매 대금이 정산되었습니다.")
        ));
    }

    @Async("notificationTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        boolean cancelledBySeller = event.cancelledBy() == OrderCancelledEvent.CancelledBy.SELLER;
        String buyerMessage = cancelledBySeller
                ? "판매자가 " + event.cardName() + " 카드 거래를 취소하여 환불되었습니다."
                : event.cardName() + " 카드 구매가 취소되어 환불되었습니다.";
        String sellerMessage = cancelledBySeller
                ? event.cardName() + " 카드 판매를 취소했습니다."
                : "구매자가 " + event.cardName() + " 카드 거래를 취소했습니다.";
        saveAllAndPush(event.auctionId(), NotificationType.ORDER_CANCELLED, List.of(
                new Recipient(event.buyerId(), buyerMessage),
                new Recipient(event.sellerId(), sellerMessage)
        ));
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
        notificationPushPublisher.publish(userId, NotificationResponse.from(notification));
    }

    /**
     * 수신자가 여럿이지만 서로 다른 메시지를 받는 경로(경매 종료의 낙찰자+판매자, 주문의 구매자+
     * 판매자) 전용. bid와 무관해 bid_id는 항상 {@link Notification#NO_BID}이므로, 수신자별
     * INSERT를 {@link NotificationService#insertAllIgnoringDuplicates}로 한 번에 합친다.
     * push는 메시지가 갈리므로 계속 수신자별로 따로 만든다. 재조회를 안 하므로 id/createdAt은
     * 근사값이다({@link #handleAuctionOpened}와 동일한 이유).
     */
    private void saveAllAndPush(Integer auctionId, NotificationType type, List<Recipient> recipients) {
        List<NotificationInsertRow> rows = recipients.stream()
                .map(recipient -> NotificationInsertRow.of(recipient.userId(), auctionId, type, recipient.message()))
                .toList();
        notificationService.insertAllIgnoringDuplicates(rows);

        Instant createdAt = Instant.now();
        for (Recipient recipient : recipients) {
            NotificationResponse payload = new NotificationResponse(
                    0L, auctionId, type, Notification.NO_BID, recipient.message(), false, createdAt);
            notificationPushPublisher.publish(recipient.userId(), payload);
        }
    }

    private record Recipient(Integer userId, String message) {
    }
}
