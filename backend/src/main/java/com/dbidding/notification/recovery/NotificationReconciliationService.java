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
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

    /**
     * 경매당 찜 유저가 많을 수 있어(인기 카드) 유저별 존재 체크 대신
     * {@link NotificationService#saveAllIgnoringDuplicates}로 한 번에 저장한다 —
     * INSERT IGNORE가 이미 있는 유저를 알아서 건너뛰므로 이 메서드에서는
     * 유니크 제약 위반 예외가 나지 않는다. 찜 유저 조회 자체도 경매마다 따로 하지 않고
     * 대상 경매들의 itemId를 모아 한 번에 조회한다(N+1 방지).
     */
    public void recoverAuctionOpenedNotifications(Instant windowStart) {
        List<Auction> recentlyOpened = auctionRepository
                .findByStatusInAndOpenTimeGreaterThanEqual(OPEN_STATUSES, windowStart);
        if (recentlyOpened.isEmpty()) {
            return;
        }

        List<Integer> itemIds = recentlyOpened.stream().map(Auction::getItemId).toList();
        Map<Integer, List<Integer>> wishlistUserIdsByCardId = wishlistService.groupUserIdsByCardIdIn(itemIds);

        for (Auction auction : recentlyOpened) {
            notificationService.saveAllIgnoringDuplicates(
                    wishlistUserIdsByCardId.getOrDefault(auction.getItemId(), List.of()),
                    auction.getId(),
                    NotificationType.AUCTION_OPENED,
                    auction.getAuctionName() + " 카드의 경매가 등록되었습니다."
            );
        }
    }

    /**
     * 낙찰 bid 조회와 알림 존재 체크를 경매마다 따로 하지 않고, 대상 경매 전체에 대해
     * 한 번씩만 배치 조회한다(N+1 방지).
     */
    public void recoverAuctionClosedNotifications(Instant windowStart) {
        List<Auction> recentlyClosed = auctionRepository
                .findByStatusInAndCloseTimeGreaterThanEqual(CLOSED_STATUSES, windowStart);
        if (recentlyClosed.isEmpty()) {
            return;
        }

        List<Integer> auctionIds = recentlyClosed.stream().map(Auction::getId).toList();
        Map<Integer, Bid> winningBidByAuctionId = bidRepository.findByAuctionIdInAndStatus(auctionIds, BidStatus.WON)
                .stream()
                .collect(Collectors.toMap(bid -> bid.getAuction().getId(), bid -> bid));

        List<Integer> candidateUserIds = new ArrayList<>();
        for (Auction auction : recentlyClosed) {
            candidateUserIds.add(auction.getSellerId());
            Bid winningBid = winningBidByAuctionId.get(auction.getId());
            if (winningBid != null) {
                candidateUserIds.add(winningBid.getBidderId());
            }
        }
        Set<String> alreadyNotified = notificationRepository
                .findByBidIdAndAuctionIdInAndUserIdIn(Notification.NO_BID, auctionIds, candidateUserIds)
                .stream()
                .map(NotificationReconciliationService::resultNotificationKey)
                .collect(Collectors.toSet());

        for (Auction auction : recentlyClosed) {
            Bid winningBid = winningBidByAuctionId.get(auction.getId());
            if (winningBid != null) {
                ensureResultNotification(
                        alreadyNotified,
                        winningBid.getBidderId(),
                        auction.getId(),
                        NotificationType.AUCTION_WON,
                        auction.getAuctionName() + " 카드 경매에 낙찰되었습니다."
                );
                ensureResultNotification(
                        alreadyNotified,
                        auction.getSellerId(),
                        auction.getId(),
                        NotificationType.AUCTION_WON,
                        auction.getAuctionName() + " 카드 경매가 낙찰되었습니다."
                );
            } else {
                ensureResultNotification(
                        alreadyNotified,
                        auction.getSellerId(),
                        auction.getId(),
                        NotificationType.AUCTION_UNSOLD,
                        auction.getAuctionName() + " 카드 경매가 유찰되었습니다."
                );
            }
        }
    }

    /**
     * 상회입찰 알림 존재 체크를 후보 bid마다 따로 하지 않고, 후보 전체에 대해 한 번만
     * 배치 조회한다(N+1 방지). bidId는 이미 특정 입찰(그 bidder/auction)을 유일하게
     * 특정하므로 type+bidId만으로 존재 확인에 충분하다.
     */
    public void recoverOutbidNotifications(Instant windowStart) {
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

        List<Bid> outbidCandidates = bidRepository.findLatestBidPerBidderByAuctionIdIn(candidateAuctionIds).stream()
                .filter(bid -> bid.getStatus() == BidStatus.OUTBID)
                .toList();
        if (outbidCandidates.isEmpty()) {
            return;
        }

        List<Long> candidateBidIds = outbidCandidates.stream().map(Bid::getId).toList();
        Set<Long> alreadyNotifiedBidIds = notificationRepository
                .findByTypeAndBidIdIn(NotificationType.OUTBID, candidateBidIds)
                .stream()
                .map(Notification::getBidId)
                .collect(Collectors.toSet());

        for (Bid latestBid : outbidCandidates) {
            if (alreadyNotifiedBidIds.contains(latestBid.getId())) {
                continue;
            }

            Integer auctionId = latestBid.getAuction().getId();
            String message = "%,d원에 상회 입찰이 발생했습니다.".formatted(latestBid.getBidPrice());
            saveIgnoringDuplicate(() -> notificationService.saveForBid(
                    latestBid.getBidderId(), auctionId, NotificationType.OUTBID, latestBid.getId(), message
            ));
        }
    }

    private void ensureResultNotification(
            Set<String> alreadyNotified, Integer userId, Integer auctionId, NotificationType type, String message
    ) {
        if (alreadyNotified.contains(resultNotificationKey(userId, auctionId, type))) {
            return;
        }
        saveIgnoringDuplicate(() -> notificationService.save(userId, auctionId, type, message));
    }

    private static String resultNotificationKey(Notification notification) {
        return resultNotificationKey(notification.getUserId(), notification.getAuctionId(), notification.getType());
    }

    private static String resultNotificationKey(Integer userId, Integer auctionId, NotificationType type) {
        return userId + ":" + auctionId + ":" + type;
    }

    private void saveIgnoringDuplicate(Runnable save) {
        try {
            save.run();
        } catch (DataIntegrityViolationException exception) {
            log.debug("event=notification.recovery.duplicate_skipped", exception);
        }
    }
}
