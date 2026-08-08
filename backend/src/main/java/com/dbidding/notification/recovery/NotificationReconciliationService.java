package com.dbidding.notification.recovery;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.notification.Notification;
import com.dbidding.notification.NotificationRepository;
import com.dbidding.notification.NotificationService;
import com.dbidding.notification.NotificationType;
import com.dbidding.wishlist.WishlistService;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * NotificationEventListener(라이브 이벤트 경로)가 비동기 처리 실패로 알림을 유실했을 때를 대비한
 * 백스톱. 이벤트 경로를 대체하지 않으며, 주기적으로 최근 상태를 다시 훑어 누락된 알림만 채운다.
 * 존재 체크와 insert 사이에 라이브 경로가 끼어들면 notification의
 * (user_id, auction_id, type, bid_id) 유니크 제약 위반이 날 수 있다 — 이미 누가 보냈다는
 * 정상 상황이므로 예외를 삼키고 다음 후보 처리를 이어간다(설계 근거:
 * docs/hamin/notification/6-notification-recovery-batch.md).
 */
@Slf4j
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

    public void recoverOutbidNotifications(LocalDateTime windowStart) {
        Set<Integer> candidateAuctionIds = new LinkedHashSet<>(bidRepository.findAuctionIdsByStatus(BidStatus.LEADING));
        // 상회입찰 직후~다음 스캔 사이에 경매가 종료되면 낙찰 bid가 LEADING→WON으로 바뀌면서
        // 위 조회에서 빠져버린다. 그 경매의 outbid된 유저들이 영영 복구 대상에서 누락되는 걸
        // 막기 위해, 최근 종료된 경매도 후보에 포함한다(낙찰자는 status=WON이라 아래에서 스킵됨).
        for (Auction recentlyClosed : auctionRepository.findByStatusInAndCloseTimeGreaterThanEqual(CLOSED_STATUSES, windowStart)) {
            candidateAuctionIds.add(recentlyClosed.getId());
        }
        if (candidateAuctionIds.isEmpty()) {
            return;
        }

        for (Bid latestBid : bidRepository.findLatestBidPerBidderByAuctionIdIn(candidateAuctionIds)) {
            if (latestBid.getStatus() != BidStatus.OUTBID) {
                continue;
            }

            Integer auctionId = latestBid.getAuction().getId();
            boolean alreadyNotified = notificationRepository.existsByUserIdAndAuctionIdAndTypeAndBidId(
                    latestBid.getBidderId(),
                    auctionId,
                    NotificationType.OUTBID,
                    latestBid.getId()
            );
            if (alreadyNotified) {
                continue;
            }

            String message = "%,d원에 상회 입찰이 발생했습니다.".formatted(latestBid.getBidPrice());
            saveIgnoringDuplicate(() -> notificationService.saveForBid(
                    latestBid.getBidderId(), auctionId, NotificationType.OUTBID, latestBid.getId(), message
            ));
        }
    }

    private void ensureNotification(Integer userId, Integer auctionId, NotificationType type, String message) {
        boolean alreadySent = notificationRepository
                .existsByUserIdAndAuctionIdAndTypeAndBidId(userId, auctionId, type, Notification.NO_BID);
        if (!alreadySent) {
            saveIgnoringDuplicate(() -> notificationService.save(userId, auctionId, type, message));
        }
    }

    private void saveIgnoringDuplicate(Runnable save) {
        try {
            save.run();
        } catch (DataIntegrityViolationException exception) {
            log.debug("event=notification.recovery.duplicate_skipped", exception);
        }
    }
}
