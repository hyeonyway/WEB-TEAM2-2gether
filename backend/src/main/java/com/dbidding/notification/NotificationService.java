package com.dbidding.notification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int FAN_OUT_CHUNK_SIZE = 10_000;

    private final NotificationRepository notificationRepository;
    private final JdbcTemplate jdbcTemplate;

    public NotificationService(NotificationRepository notificationRepository, JdbcTemplate jdbcTemplate) {
        this.notificationRepository = notificationRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Notification save(Integer userId, Integer auctionId, NotificationType type, String message) {
        return notificationRepository.save(Notification.of(userId, auctionId, type, message));
    }

    @Transactional
    public Notification saveForBid(Integer userId, Integer auctionId, NotificationType type, Long bidId, String message) {
        return notificationRepository.save(Notification.ofBid(userId, auctionId, type, bidId, message));
    }

    /**
     * 여러 유저에게 같은 알림을 INSERT로 저장한다(경매 생성 fan-out 등). 복구 배치나 다른 경로가
     * 특정 유저에 대해 이미 저장해뒀을 수 있으므로 {@code INSERT IGNORE}로 유니크 제약(user_id,
     * auction_id, type, bid_id) 위반 행은 조용히 건너뛰고, 이미 있던 행과 새로 저장된 행을 한 번의
     * 조회로 함께 가져와 반환한다. bid와 무관한 알림 전용이라 bid_id는 항상 {@link Notification#NO_BID}다.
     * INSERT는 유저 1명당 플레이스홀더 5개를 쓰므로 MySQL 프리페어드 스테이트먼트 한도(65,535개)에
     * 걸릴 수 있어 {@link #FAN_OUT_CHUNK_SIZE} 단위로 나눠 실행한다. 재조회 SELECT는 유저 1명당
     * 플레이스홀더 1개라 훨씬 여유 있어 청크 없이 전체 유저를 한 번에 조회한다.
     */
    @Transactional
    public List<Notification> saveAllIgnoringDuplicates(
            List<Integer> userIds, Integer auctionId, NotificationType type, String message
    ) {
        if (userIds.isEmpty()) {
            return List.of();
        }

        for (int from = 0; from < userIds.size(); from += FAN_OUT_CHUNK_SIZE) {
            List<Integer> chunk = userIds.subList(from, Math.min(from + FAN_OUT_CHUNK_SIZE, userIds.size()));
            insertIgnoringDuplicates(chunk, auctionId, type, message);
        }

        return notificationRepository.findByAuctionIdAndTypeAndBidIdAndUserIdIn(
                auctionId, type, Notification.NO_BID, userIds
        );
    }

    private void insertIgnoringDuplicates(
            List<Integer> userIds, Integer auctionId, NotificationType type, String message
    ) {
        String placeholders = String.join(", ", Collections.nCopies(userIds.size(), "(?, ?, ?, ?, ?)"));
        String sql = "INSERT IGNORE INTO notification (user_id, auction_id, type, bid_id, message) VALUES " + placeholders;
        List<Object> args = new ArrayList<>(userIds.size() * 5);
        for (Integer userId : userIds) {
            args.add(userId);
            args.add(auctionId);
            args.add(type.name());
            args.add(Notification.NO_BID);
            args.add(message);
        }
        jdbcTemplate.update(sql, args.toArray());
    }

    public NotificationPage findPage(Integer userId, Long cursor, int size, boolean unreadOnly) {
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw NotificationException.invalidPageSize("size는 %d에서 %d 사이여야 합니다.".formatted(MIN_PAGE_SIZE, MAX_PAGE_SIZE));
        }
        Pageable pageable = PageRequest.of(0, size + 1);
        List<Notification> fetched = fetch(userId, cursor, unreadOnly, pageable);
        boolean hasNext = fetched.size() > size;
        List<Notification> items = hasNext ? fetched.subList(0, size) : fetched;
        Long nextCursor = hasNext ? items.get(items.size() - 1).getId() : null;
        return new NotificationPage(items, nextCursor, hasNext);
    }

    private List<Notification> fetch(Integer userId, Long cursor, boolean unreadOnly, Pageable pageable) {
        if (unreadOnly) {
            return cursor == null
                    ? notificationRepository.findByUserIdAndIsReadFalseOrderByIdDesc(userId, pageable)
                    : notificationRepository.findByUserIdAndIsReadFalseAndIdLessThanOrderByIdDesc(userId, cursor, pageable);
        }
        return cursor == null
                ? notificationRepository.findByUserIdOrderByIdDesc(userId, pageable)
                : notificationRepository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, pageable);
    }

    public long countUnread(Integer userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(Integer userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .filter(found -> found.getUserId().equals(userId))
                .orElseThrow(NotificationException::notFound);
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}
