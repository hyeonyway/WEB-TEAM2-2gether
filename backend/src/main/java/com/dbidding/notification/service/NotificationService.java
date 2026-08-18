package com.dbidding.notification.service;

import com.dbidding.notification.domain.Notification;
import com.dbidding.notification.domain.NotificationType;
import com.dbidding.notification.dto.NotificationInsertRow;
import com.dbidding.notification.dto.NotificationPage;
import com.dbidding.notification.exception.NotificationException;
import com.dbidding.notification.repository.NotificationRepository;
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
     * auction_id, type, bid_id) 위반 행은 조용히 건너뛴다. #505 이후 SSE push/markAsRead가 DB
     * PK인 id 없이 (userId, type, auctionId/bidId) 복합키만으로 동작하므로, 저장된 행을 다시
     * 읽어올 필요가 없다 — 재조회 SELECT를 하지 않는다(호출부가 push 페이로드를 직접 구성한다).
     * bid와 무관한 알림 전용이라 bid_id는 항상 {@link Notification#NO_BID}다. INSERT는 유저
     * 1명당 플레이스홀더 5개를 쓰므로 MySQL 프리페어드 스테이트먼트 한도(65,535개)에 걸릴 수
     * 있어 {@link #FAN_OUT_CHUNK_SIZE} 단위로 나눠 실행한다.
     */
    @Transactional
    public void saveAllIgnoringDuplicates(
            List<Integer> userIds, Integer auctionId, NotificationType type, String message
    ) {
        if (userIds.isEmpty()) {
            return;
        }

        for (int from = 0; from < userIds.size(); from += FAN_OUT_CHUNK_SIZE) {
            List<Integer> chunk = userIds.subList(from, Math.min(from + FAN_OUT_CHUNK_SIZE, userIds.size()));
            insertIgnoringDuplicates(chunk, auctionId, type, message);
        }
    }

    /**
     * 여러 경매/후보의 알림을 한 번에 INSERT로 저장한다(복구 배치 전용, 이슈 #373, #414). 라이브
     * 이벤트 경로({@link #saveAllIgnoringDuplicates})와 달리 SSE push가 필요 없어
     * 재조회 SELECT를 하지 않는다. 행마다 type/bid_id가 다를 수 있어(경매종료의
     * AUCTION_WON/AUCTION_UNSOLD, 상회입찰의 개별 bid_id) 공통 type을 인자로 받지 않고
     * {@link NotificationInsertRow}에 담아온 값을 그대로 쓴다. 행 1개당 {@code ?} 5개를
     * 쓰므로 {@link #FAN_OUT_CHUNK_SIZE} 단위로 나눠 INSERT한다.
     */
    @Transactional
    public void insertAllIgnoringDuplicates(List<NotificationInsertRow> rows) {
        for (int from = 0; from < rows.size(); from += FAN_OUT_CHUNK_SIZE) {
            List<NotificationInsertRow> chunk = rows.subList(from, Math.min(from + FAN_OUT_CHUNK_SIZE, rows.size()));
            insertRowsIgnoringDuplicates(chunk);
        }
    }

    private void insertRowsIgnoringDuplicates(List<NotificationInsertRow> rows) {
        String placeholders = String.join(", ", Collections.nCopies(rows.size(), "(?, ?, ?, ?, ?)"));
        String sql = "INSERT IGNORE INTO notification (user_id, auction_id, type, bid_id, message) VALUES " + placeholders;
        List<Object> args = new ArrayList<>(rows.size() * 5);
        for (NotificationInsertRow row : rows) {
            args.add(row.userId());
            args.add(row.auctionId());
            args.add(row.type().name());
            args.add(row.bidId());
            args.add(row.message());
        }
        jdbcTemplate.update(sql, args.toArray());
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
    public void markAsRead(Integer userId, NotificationType type, Integer auctionId, Long bidId) {
        Notification notification = notificationRepository
                .findByUserIdAndAuctionIdAndTypeAndBidId(userId, auctionId, type, bidId)
                .orElseThrow(NotificationException::notFound);
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}
