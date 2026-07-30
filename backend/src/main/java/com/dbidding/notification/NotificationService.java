package com.dbidding.notification;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public Notification save(Integer userId, Integer auctionId, String message) {
        return notificationRepository.save(Notification.of(userId, auctionId, message));
    }

    public NotificationPage findPage(Integer userId, Long cursor, int size, boolean unreadOnly) {
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않는 알림입니다."));
        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(Integer userId) {
        notificationRepository.markAllAsReadByUserId(userId);
    }
}
