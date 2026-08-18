package com.dbidding.notification.recovery;

import com.dbidding.auction.domain.Auction;
import com.dbidding.auction.domain.AuctionStatus;
import com.dbidding.auction.domain.Bid;
import com.dbidding.auction.domain.BidStatus;
import com.dbidding.auction.repository.AuctionRepository;
import com.dbidding.auction.repository.BidRepository;
import com.dbidding.notification.dto.NotificationInsertRow;
import com.dbidding.notification.service.NotificationService;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.wishlist.service.WishlistService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.stereotype.Service;

/**
 * NotificationEventListener(라이브 이벤트 경로)가 비동기 처리 실패로 알림을 유실했을 때를 대비한
 * 백스톱. 이벤트 경로를 대체하지 않으며, 주기적으로 최근 상태를 다시 훑어 누락된 알림만 채운다.
 * notification의 (user_id, auction_id, type, bid_id) 유니크 제약(설계 근거:
 * docs/hamin/notification/6-notification-recovery-batch.md 결정 2-1)에 의존해 사전 존재
 * 체크 없이 INSERT IGNORE로 바로 저장한다 — 라이브 경로가 먼저 저장해둔 행과 겹쳐도
 * 조용히 건너뛴다(이슈 #414).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationReconciliationService {

    private static final List<AuctionStatus> OPEN_STATUSES = List.of(AuctionStatus.OPEN, AuctionStatus.ENDING);
    private static final List<AuctionStatus> CLOSED_STATUSES = List.of(AuctionStatus.ENDED, AuctionStatus.FAILED);
    private static final int DEADLOCK_MAX_ATTEMPTS = 3;
    private static final long DEADLOCK_RETRY_BACKOFF_MS = 100;

    private final AuctionRepository auctionRepository;
    private final BidRepository bidRepository;
    private final WishlistService wishlistService;
    private final NotificationService notificationService;

    /**
     * 경매당 찜 유저가 많을 수 있어(인기 카드) 유저별 존재 체크 대신
     * {@link NotificationService#insertAllIgnoringDuplicates}로 한 번에 저장한다 —
     * INSERT IGNORE가 이미 있는 유저를 알아서 건너뛰므로 이 메서드에서는
     * 유니크 제약 위반 예외가 나지 않는다. 찜 유저 조회 자체도 경매마다 따로 하지 않고
     * 대상 경매들의 itemId를 모아 한 번에 조회한다(N+1 방지). 이번 윈도우에 경매가 여러
     * 개 열렸어도 경매마다 따로 INSERT하지 않고 전부 한 번에(청크 단위로) INSERT한다
     * (이슈 #373) — 복구 배치는 라이브 이벤트 경로처럼 SSE push용 재조회가 필요 없어서
     * 가능한 최적화다.
     */
    public void recoverAuctionOpenedNotifications(Instant windowStart) {
        List<Auction> recentlyOpened = auctionRepository
                .findByStatusInAndOpenTimeGreaterThanEqual(OPEN_STATUSES, windowStart);
        if (recentlyOpened.isEmpty()) {
            return;
        }

        List<Integer> itemIds = recentlyOpened.stream().map(Auction::getItemId).toList();
        Map<Integer, List<Integer>> wishlistUserIdsByCardId = wishlistService.groupUserIdsByCardIdIn(itemIds);

        List<NotificationInsertRow> rows = new ArrayList<>();
        for (Auction auction : recentlyOpened) {
            String message = auction.getAuctionName() + " 카드의 경매가 등록되었습니다.";
            for (Integer userId : wishlistUserIdsByCardId.getOrDefault(auction.getItemId(), List.of())) {
                rows.add(NotificationInsertRow.of(userId, auction.getId(), NotificationType.AUCTION_OPENED, message));
            }
        }
        insertAllIgnoringDuplicatesWithDeadlockRetry(rows);
    }

    /**
     * 낙찰 bid 조회를 경매마다 따로 하지 않고 대상 경매 전체에 대해 한 번만 배치 조회한다
     * (N+1 방지). 알림 존재 체크는 하지 않는다 — {@code (user_id, auction_id, type, bid_id)}
     * 유니크 제약이 있어(설계 근거: docs/hamin/notification/6-notification-recovery-batch.md
     * 결정 2-1) {@link NotificationService#insertAllIgnoringDuplicates}의 INSERT IGNORE가
     * 중복 행을 알아서 건너뛴다(이슈 #414).
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

        List<NotificationInsertRow> rows = new ArrayList<>();
        for (Auction auction : recentlyClosed) {
            Bid winningBid = winningBidByAuctionId.get(auction.getId());
            if (winningBid != null) {
                rows.add(NotificationInsertRow.of(
                        winningBid.getBidderId(), auction.getId(), NotificationType.AUCTION_WON,
                        auction.getAuctionName() + " 카드 경매에 낙찰되었습니다."
                ));
                rows.add(NotificationInsertRow.of(
                        auction.getSellerId(), auction.getId(), NotificationType.AUCTION_WON,
                        auction.getAuctionName() + " 카드 경매가 낙찰되었습니다."
                ));
            } else {
                rows.add(NotificationInsertRow.of(
                        auction.getSellerId(), auction.getId(), NotificationType.AUCTION_UNSOLD,
                        auction.getAuctionName() + " 카드 경매가 유찰되었습니다."
                ));
            }
        }
        insertAllIgnoringDuplicatesWithDeadlockRetry(rows);
    }

    /**
     * ENDING 경매의 상회입찰 복구(이슈 #373, 긴급/짧은 주기). ENDING은 앤티스나이핑
     * 자동 연장을 트리거한 입찰이 있어야만 도달하는 상태라({@link Auction#placeBid}가
     * 부르는 연장 로직), 그 트리거 입찰이 항상 LEADING으로 남아있음이 구조적으로 보장된다
     * — 그래서 {@code bids} 조인 없이 {@code auctions.status}만으로 후보를 뽑는다.
     */
    public void recoverEndingOutbidNotifications(Instant windowStart) {
        Set<Integer> candidateAuctionIds = new LinkedHashSet<>(
                auctionRepository.findIdsByStatus(AuctionStatus.ENDING));
        recoverOutbidNotificationsForCandidates(candidateAuctionIds, windowStart);
    }

    /**
     * OPEN 경매의 상회입찰 복구(이슈 #373, 비긴급/긴 주기). {@code bids.status=LEADING}인
     * bid의 경매 id를 그대로 후보로 쓴다 — {@code auctions}와 join해서 OPEN만 걸러내지
     * 않는다(이슈 #416). LEADING bid는 구조상 OPEN 또는 ENDING 경매에서만 존재하므로,
     * 이 조회는 결과적으로 ENDING 경매도 같이 잡는다. ENDING은 urgent(90초 주기,
     * {@link #recoverEndingOutbidNotifications})가 이미 더 빠르게 처리하므로 여기서
     * 겹쳐 처리해도 손해가 없다 — INSERT IGNORE(유니크 제약 (user_id, auction_id, type,
     * bid_id))가 중복 저장을 조용히 걸러주고, urgent가 window를 놓친 경우엔 오히려
     * 5분 주기 백업 백스톱이 된다. auctions join 없이 {@code bids.status} 단일 인덱스만
     * 쓰므로 OPEN만 필터링하던 이전 버전보다 비용도 줄었다.
     */
    public void recoverOpenOutbidNotifications(Instant windowStart) {
        Set<Integer> candidateAuctionIds = new LinkedHashSet<>(
                bidRepository.findAuctionIdsByStatus(BidStatus.LEADING));
        recoverOutbidNotificationsForCandidates(candidateAuctionIds, windowStart);
    }

    /**
     * 상회입찰 알림 존재 체크는 하지 않는다 — {@code (user_id, auction_id, type, bid_id)}
     * 유니크 제약이 있어(설계 근거: docs/hamin/notification/6-notification-recovery-batch.md
     * 결정 2-1) {@link NotificationService#insertAllIgnoringDuplicates}의 INSERT IGNORE가
     * 중복 행을 알아서 건너뛴다(이슈 #414).
     * 상회입찰 직후~다음 스캔 사이에 경매가 종료되면 낙찰 bid가 LEADING→WON으로 바뀌면서
     * 위 두 메서드의 조회에서 빠져버린다. 그 경매의 outbid된 유저들이 영영 복구 대상에서
     * 누락되는 걸 막기 위해, 최근 종료된 경매도 후보에 포함한다(낙찰자는 status=WON이라
     * 아래에서 스킵됨) — ENDING/OPEN 어느 쪽 호출이든 항상 수행한다. 종료 직전 경매가
     * ENDING을 거치지 않고 OPEN에서 바로 닫히는 경우도 있어서, 한쪽 상태에만 이 캐치를
     * 묶으면 다른 쪽에서 종료 경계 유실 버그가 재발하기 때문이다.
     */
    private void recoverOutbidNotificationsForCandidates(Set<Integer> candidateAuctionIds, Instant windowStart) {
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

        List<NotificationInsertRow> rows = new ArrayList<>();
        for (Bid latestBid : outbidCandidates) {
            String message = "%,d원에 상회 입찰이 발생했습니다.".formatted(latestBid.getBidPrice());
            rows.add(new NotificationInsertRow(
                    latestBid.getBidderId(), latestBid.getAuction().getId(), NotificationType.OUTBID, latestBid.getId(), message
            ));
        }
        insertAllIgnoringDuplicatesWithDeadlockRetry(rows);
    }

    /**
     * urgent/non-urgent 스케줄러가 같은 대상 경매를 겹쳐 처리할 수 있어(이슈 #429, ENDING 상회입찰과
     * 최근 종료 경매 후보가 양쪽에서 겹침) {@code notification} 유니크 키 range에 대한 gap lock
     * 경합으로 {@link CannotAcquireLockException}(MySQL 데드락)이 드물게 발생한다. MySQL은 데드락
     * 희생 트랜잭션을 통째로 롤백하므로, 재시도는 반드시 새 트랜잭션으로 다시 시도해야 한다 —
     * {@link NotificationService#insertAllIgnoringDuplicates}를 다시 호출하면 프록시를 통한
     * 외부 호출이라 매 시도마다 새 트랜잭션이 열린다(같은 클래스 안에서 재시도했다면 이미 롤백된
     * 트랜잭션에 대고 재시도하는 셈이라 무효했을 것). INSERT IGNORE(#414)가 멱등이라 이미 커밋된
     * 청크를 포함해 전체를 다시 실행해도 안전하다.
     */
    private void insertAllIgnoringDuplicatesWithDeadlockRetry(List<NotificationInsertRow> rows) {
        for (int attempt = 1; ; attempt++) {
            try {
                notificationService.insertAllIgnoringDuplicates(rows);
                return;
            } catch (CannotAcquireLockException exception) {
                if (attempt >= DEADLOCK_MAX_ATTEMPTS) {
                    throw exception;
                }
                log.warn("event=notification.recovery.insert.deadlock.retry attempt={}", attempt, exception);
                sleepBeforeRetry(DEADLOCK_RETRY_BACKOFF_MS * attempt);
            }
        }
    }

    private void sleepBeforeRetry(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("알림 복구 배치 데드락 재시도 대기 중 인터럽트됨", interruptedException);
        }
    }
}
