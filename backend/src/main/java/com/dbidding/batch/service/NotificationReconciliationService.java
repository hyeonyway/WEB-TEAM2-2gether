package com.dbidding.batch.service;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.notification.NotificationRepository;
import com.dbidding.notification.NotificationService;
import com.dbidding.notification.NotificationType;
import com.dbidding.wishlist.WishlistService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * NotificationEventListener(라이브 이벤트 경로)가 비동기 처리 실패로 알림을 유실했을 때를 대비한
 * 백스톱. 이벤트 경로를 대체하지 않으며, 주기적으로 최근 상태를 다시 훑어 누락된 알림만 채운다.
 * 존재 체크와 insert 사이 레이스로 라이브 경로와 중복 알림이 갈 수 있으나, 유실보다 나은
 * 실패 모드로 보고 감수한다(설계 근거: docs/hamin/notification/6-notification-recovery-batch.md).
 */
@Service
@RequiredArgsConstructor
public class NotificationReconciliationService {

    private static final List<AuctionStatus> OPEN_STATUSES = List.of(AuctionStatus.OPEN, AuctionStatus.ENDING);
    private static final List<AuctionStatus> CLOSED_STATUSES = List.of(AuctionStatus.ENDED, AuctionStatus.FAILED);

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WishlistService wishlistService;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public void recoverAuctionOpenedNotifications(LocalDateTime windowStart) {
        List<Auction> recentlyOpened = auctionRepository
                .findByStatusInAndOpenTimeGreaterThanEqual(OPEN_STATUSES, windowStart);

        for (Auction auction : recentlyOpened) {
            for (Integer userId : wishlistService.findUserIdsByCardId(auction.getItemId())) {
                ensureNotification(
                        userId,
                        auction.getId(),
                        NotificationType.AUCTION_OPENED,
                        auction.getAuctionName() + " 카드의 경매가 등록되었습니다."
                );
            }
        }
    }

    public void recoverAuctionClosedNotifications(LocalDateTime windowStart) {
        List<Auction> recentlyClosed = auctionRepository
                .findByStatusInAndCloseTimeGreaterThanEqual(CLOSED_STATUSES, windowStart);

        for (Auction auction : recentlyClosed) {
            Optional<Bid> winningBid = bidRepository.findByAuctionIdAndStatus(auction.getId(), BidStatus.WON);

            if (winningBid.isPresent()) {
                ensureNotification(
                        winningBid.get().getBidderId(),
                        auction.getId(),
                        NotificationType.AUCTION_WON,
                        auction.getAuctionName() + " 카드 경매에 낙찰되었습니다."
                );
                ensureNotification(
                        auction.getSellerId(),
                        auction.getId(),
                        NotificationType.AUCTION_WON,
                        auction.getAuctionName() + " 카드 경매가 낙찰되었습니다."
                );
            } else {
                ensureNotification(
                        auction.getSellerId(),
                        auction.getId(),
                        NotificationType.AUCTION_UNSOLD,
                        auction.getAuctionName() + " 카드 경매가 유찰되었습니다."
                );
            }
        }
    }

    public void recoverOutbidNotifications() {
        List<Integer> activeAuctionIds = bidRepository.findAuctionIdsByStatus(BidStatus.LEADING);
        if (activeAuctionIds.isEmpty()) {
            return;
        }

        for (Bid latestBid : bidRepository.findLatestBidPerBidderByAuctionIdIn(activeAuctionIds)) {
            if (latestBid.getStatus() != BidStatus.OUTBID) {
                continue;
            }

            Integer auctionId = latestBid.getAuction().getId();
            boolean alreadyNotified = notificationRepository.existsByUserIdAndAuctionIdAndTypeAndCreatedAtAfter(
                    latestBid.getBidderId(),
                    auctionId,
                    NotificationType.OUTBID,
                    latestBid.getCreatedAt()
            );
            if (alreadyNotified) {
                continue;
            }

            String message = "%,d원에 상회 입찰이 발생했습니다.".formatted(latestBid.getBidPrice());
            notificationService.save(latestBid.getBidderId(), auctionId, NotificationType.OUTBID, message);
        }
    }

    private void ensureNotification(Integer userId, Integer auctionId, NotificationType type, String message) {
        boolean alreadySent = notificationRepository.existsByUserIdAndAuctionIdAndType(userId, auctionId, type);
        if (!alreadySent) {
            notificationService.save(userId, auctionId, type, message);
        }
    }
}
