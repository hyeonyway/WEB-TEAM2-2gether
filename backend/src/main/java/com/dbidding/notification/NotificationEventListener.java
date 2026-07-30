package com.dbidding.notification;

import com.dbidding.notification.event.AuctionClosedEvent;
import com.dbidding.notification.event.AuctionCreatedEvent;
import com.dbidding.notification.event.BidOutbidEvent;
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
    private final NotificationService notificationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionCreated(AuctionCreatedEvent event) {
        String message = event.cardName() + " 카드의 경매가 등록되었습니다.";
        wishlistUserFinder.findUserIdsByCardId(event.cardId())
                .forEach(userId -> notificationService.save(userId, event.auctionId(), message));
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleBidOutbid(BidOutbidEvent event) {
        String message = event.cardName() + " 카드 경매에 상회 입찰이 발생했습니다.";
        notificationService.save(event.previousBidderId(), event.auctionId(), message);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAuctionClosed(AuctionClosedEvent event) {
        boolean won = event.winnerId() != null;
        if (won) {
            String winnerMessage = event.cardName() + " 카드 경매에 낙찰되었습니다.";
            notificationService.save(event.winnerId(), event.auctionId(), winnerMessage);
        }
        String sellerMessage = event.cardName() + " 카드 경매가 " + (won ? "낙찰되었습니다." : "유찰되었습니다.");
        notificationService.save(event.sellerId(), event.auctionId(), sellerMessage);
    }
}
