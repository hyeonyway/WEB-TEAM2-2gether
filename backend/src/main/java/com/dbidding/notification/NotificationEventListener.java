package com.dbidding.notification;

import com.dbidding.auction.event.AuctionClosedEvent;
import com.dbidding.auction.event.AuctionOpenedEvent;
import com.dbidding.auction.event.BidPlacedEvent;
import com.dbidding.notification.dto.NotificationResponse;
import com.dbidding.notification.port.CardNameFinder;
import com.dbidding.notification.port.WishlistUserFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final WishlistUserFinder wishlistUserFinder;
    private final CardNameFinder cardNameFinder;
    private final NotificationService notificationService;
    private final NotificationSseConnectionManager notificationSseConnectionManager;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionOpened(AuctionOpenedEvent event) {
        String message = event.cardName() + " 카드의 경매가 등록되었습니다.";
        wishlistUserFinder.findUserIdsByCardId(event.itemId())
                .forEach(userId -> notifyAndPush(userId, event.auctionId(), NotificationType.AUCTION_OPENED, message));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBidPlaced(BidPlacedEvent event) {
        if (event.previousBidderId() == null) {
            return;
        }
        String cardName = cardNameFinder.findNameById(event.itemId());
        String message = cardName + " 카드 경매에 " + "%,d".formatted(event.currentPrice()) + "원에 상회 입찰이 발생했습니다.";
        Notification saved = notificationService.saveForBid(
                event.previousBidderId(), event.auctionId(), NotificationType.OUTBID, event.previousBidId(), message
        );
        notificationSseConnectionManager.push(event.previousBidderId(), NotificationResponse.from(saved));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        boolean won = event.winnerId() != null;
        NotificationType type = won ? NotificationType.AUCTION_WON : NotificationType.AUCTION_UNSOLD;
        if (won) {
            String winnerMessage = event.cardName() + " 카드 경매에 낙찰되었습니다.";
            notifyAndPush(event.winnerId(), event.auctionId(), type, winnerMessage);
        }
        String sellerMessage = event.cardName() + " 카드 경매가 " + (won ? "낙찰되었습니다." : "유찰되었습니다.");
        notifyAndPush(event.sellerId(), event.auctionId(), type, sellerMessage);
    }

    private void notifyAndPush(Integer userId, Integer auctionId, NotificationType type, String message) {
        Notification saved = notificationService.save(userId, auctionId, type, message);
        notificationSseConnectionManager.push(userId, NotificationResponse.from(saved));
    }
}
